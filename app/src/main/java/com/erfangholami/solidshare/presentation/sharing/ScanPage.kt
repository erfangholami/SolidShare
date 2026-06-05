package com.erfangholami.solidshare.presentation.sharing

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraControl
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import com.erfangholami.solidshare.R
import com.erfangholami.solidshare.presentation.navigation.ConfirmAccessRoute
import com.erfangholami.solidshare.presentation.navigation.PublicProfileRoute
import com.erfangholami.solidshare.presentation.util.pasteText
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.launch

private val CameraViewHeight = 400.dp
private val ScanWindowSize = 270.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanPage(
    navController: NavController,
    viewModel: ScanViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val notRecognizedMsg = stringResource(R.string.scan_not_recognized)

    val activity = remember(context) { context.findActivity() }
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                    == PackageManager.PERMISSION_GRANTED,
        )
    }
    var permanentlyDenied by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasPermission = granted
        if (!granted && activity != null) {
            permanentlyDenied = !ActivityCompat.shouldShowRequestPermissionRationale(
                activity,
                Manifest.permission.CAMERA,
            )
        }
    }

    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val granted = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.CAMERA,
                ) == PackageManager.PERMISSION_GRANTED
                hasPermission = granted
                if (granted) permanentlyDenied = false
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val requestCameraPermission: () -> Unit = {
        if (permanentlyDenied) {
            showSettingsDialog = true
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    var scanResetToken by remember { mutableIntStateOf(0) }

    val onResult: (String) -> Unit = { raw ->
        when (val target = viewModel.classify(raw)) {
            is ScanViewModel.ScanTarget.Share ->
                navController.navigate(
                    ConfirmAccessRoute(target.resourceUri, target.ownerWebId),
                )

            is ScanViewModel.ScanTarget.Profile ->
                navController.navigate(PublicProfileRoute(target.webId))

            ScanViewModel.ScanTarget.Unrecognized -> {
                scope.launch { snackbarHostState.showSnackbar(notRecognizedMsg) }
                scanResetToken++
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.scan_qr),
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.close))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            key(scanResetToken) {
                ScannerContent(
                    subtitle = stringResource(R.string.scan_universal_subtitle),
                    hasPermission = hasPermission,
                    onRequestPermission = requestCameraPermission,
                    onResult = onResult,
                )
            }
        }
    }

    if (showSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            title = { Text(stringResource(R.string.camera_permission_title)) },
            text = { Text(stringResource(R.string.camera_permission_rationale)) },
            confirmButton = {
                TextButton(onClick = {
                    showSettingsDialog = false
                    activity?.startActivity(
                        Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", context.packageName, null),
                        ),
                    )
                }) { Text(stringResource(R.string.open_settings)) }
            },
            dismissButton = {
                TextButton(onClick = { showSettingsDialog = false }) {
                    Text(stringResource(R.string.close))
                }
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ScannerContent(
    subtitle: String,
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
    onResult: (String) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboard.current
    var flashOn by remember { mutableStateOf(false) }
    var hasFired by remember { mutableStateOf(false) }
    var linkInput by rememberSaveable { mutableStateOf("") }

    val scrollState = rememberScrollState()
    var fieldFocused by remember { mutableStateOf(false) }
    val imeVisible = WindowInsets.isImeVisible
    LaunchedEffect(imeVisible, fieldFocused) {
        if (imeVisible && fieldFocused) {
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }

    val scanLineColor = MaterialTheme.colorScheme.primary
    val transition = rememberInfiniteTransition(label = "scan")
    val lineFraction by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "scanLine",
    )

    val scanner = remember {
        BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build(),
        )
    }
    DisposableEffect(scanner) {
        onDispose { scanner.close() }
    }

    val cameraControlHolder = remember { mutableStateOf<CameraControl?>(null) }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                runCatching { InputImage.fromFilePath(context, uri) }
                    .onSuccess { input ->
                        scanner.process(input).addOnSuccessListener { barcodes ->
                            val raw = barcodes.firstNotNullOfOrNull { it.rawValue }
                            if (!raw.isNullOrBlank() && !hasFired) {
                                hasFired = true
                                onResult(raw)
                            }
                        }
                    }
            }
        }
    }

    LaunchedEffect(flashOn) {
        cameraControlHolder.value?.enableTorch(flashOn)
    }

    val submitLink: () -> Unit = {
        val value = linkInput.trim()
        if (value.isNotEmpty() && !hasFired) {
            hasFired = true
            onResult(value)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(scrollState)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(CameraViewHeight)
                .clip(RoundedCornerShape(24.dp))
                .background(Color.Black),
        ) {
            if (hasPermission) {
                AndroidView(
                    factory = { ctx ->
                        PreviewView(ctx).also { preview ->
                            bindCamera(
                                context = ctx,
                                lifecycleOwner = lifecycleOwner,
                                previewView = preview,
                                scanner = scanner,
                                onCameraControl = { cameraControlHolder.value = it },
                                onScan = { raw ->
                                    if (!hasFired) {
                                        hasFired = true
                                        onResult(raw)
                                    }
                                },
                            )
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )

                Canvas(modifier = Modifier.fillMaxSize()) {
                    val side = minOf(ScanWindowSize.toPx(), size.width - 32.dp.toPx())
                    val left = (size.width - side) / 2f
                    val top = 32.dp.toPx()
                    val r = 22.dp.toPx()
                    val arm = 26.dp.toPx()
                    val windowRect = Rect(left, top, left + side, top + side)

                    val scrim = Path().apply {
                        addRect(Rect(0f, 0f, size.width, size.height))
                        addRoundRect(RoundRect(windowRect, CornerRadius(r, r)))
                        fillType = PathFillType.EvenOdd
                    }
                    drawPath(scrim, Color.Black.copy(alpha = 0.5f))

                    val cornerStroke = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                    drawPath(
                        Path().apply {
                            moveTo(left, top + r + arm)
                            lineTo(left, top + r)
                            arcTo(Rect(left, top, left + 2 * r, top + 2 * r), 180f, 90f, false)
                            lineTo(left + r + arm, top)
                        },
                        color = Color.White,
                        style = cornerStroke,
                    )
                    drawPath(
                        Path().apply {
                            moveTo(left + side - r - arm, top)
                            lineTo(left + side - r, top)
                            arcTo(Rect(left + side - 2 * r, top, left + side, top + 2 * r), 270f, 90f, false)
                            lineTo(left + side, top + r + arm)
                        },
                        color = Color.White,
                        style = cornerStroke,
                    )
                    drawPath(
                        Path().apply {
                            moveTo(left + side, top + side - r - arm)
                            lineTo(left + side, top + side - r)
                            arcTo(
                                Rect(left + side - 2 * r, top + side - 2 * r, left + side, top + side),
                                0f, 90f, false,
                            )
                            lineTo(left + side - r - arm, top + side)
                        },
                        color = Color.White,
                        style = cornerStroke,
                    )
                    drawPath(
                        Path().apply {
                            moveTo(left + r + arm, top + side)
                            lineTo(left + r, top + side)
                            arcTo(Rect(left, top + side - 2 * r, left + 2 * r, top + side), 90f, 90f, false)
                            lineTo(left, top + side - r - arm)
                        },
                        color = Color.White,
                        style = cornerStroke,
                    )

                    val inset = 12.dp.toPx()
                    val lineY = top + r + lineFraction * (side - 2 * r)
                    drawLine(
                        color = scanLineColor,
                        start = Offset(left + inset, lineY),
                        end = Offset(left + side - inset, lineY),
                        strokeWidth = 3.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        Icons.Outlined.PhotoCamera,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(36.dp),
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.camera_permission_needed),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onRequestPermission) {
                        Text(stringResource(R.string.grant_permission))
                    }
                }
            }

            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { flashOn = !flashOn }, enabled = hasPermission) {
                    Icon(
                        imageVector = if (flashOn) Icons.Filled.FlashOff else Icons.Filled.FlashOn,
                        contentDescription = if (flashOn) stringResource(R.string.flash_off)
                        else stringResource(R.string.flash_on),
                        tint = if (hasPermission) Color.White else Color.White.copy(alpha = 0.4f),
                    )
                }
                Spacer(Modifier.size(32.dp))
                IconButton(onClick = {
                    galleryLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                }) {
                    Icon(
                        Icons.Outlined.PhotoLibrary,
                        contentDescription = stringResource(R.string.pick_image),
                        tint = Color.White,
                    )
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = linkInput,
            onValueChange = { linkInput = it },
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { fieldFocused = it.isFocused },
            placeholder = { Text(stringResource(R.string.scan_paste_hint)) },
            singleLine = true,
            trailingIcon = {
                IconButton(onClick = {
                    scope.launch { clipboard.pasteText()?.let { linkInput = it } }
                }) {
                    Icon(
                        Icons.Outlined.ContentPaste,
                        contentDescription = stringResource(R.string.paste_from_clipboard),
                    )
                }
            },
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = submitLink,
            enabled = linkInput.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.open_share))
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
