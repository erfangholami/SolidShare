package com.erfangholami.solidshare.presentation.sharing

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraControl
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.erfangholami.solidshare.R
import com.erfangholami.solidshare.presentation.util.pasteText
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanReceivedSharePage(
    navController: NavController,
    viewModel: ScanReceivedShareViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val addedMsg = stringResource(R.string.added_to_shares)
    val requestSentMsg = stringResource(R.string.access_request_sent)

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                    == PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> hasPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    LaunchedEffect(state) {
        when (val s = state) {
            is ScanReceivedShareViewModel.State.Granted -> {
                navController.previousBackStackEntry?.savedStateHandle
                    ?.set("received_share_msg", addedMsg)
                navController.popBackStack()
            }

            is ScanReceivedShareViewModel.State.RequestSent -> {
                navController.previousBackStackEntry?.savedStateHandle
                    ?.set("received_share_msg", requestSentMsg)
                navController.popBackStack()
            }

            is ScanReceivedShareViewModel.State.Failure -> {
                scope.launch { snackbarHostState.showSnackbar(s.message) }
                viewModel.resetToScanning()
            }

            else -> Unit
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.scan_received_title),
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
            if (!hasPermission) {
                PermissionPrompt(
                    onRequest = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                    onClose = { navController.popBackStack() },
                )
            } else when (state) {
                is ScanReceivedShareViewModel.State.Scanning ->
                    ScannerContent(
                        onScanned = viewModel::verify,
                        onPasted = viewModel::verify,
                    )

                is ScanReceivedShareViewModel.State.Verifying ->
                    LoadingBox(label = stringResource(R.string.checking_access))

                else -> Unit
            }
        }
    }

    (state as? ScanReceivedShareViewModel.State.NotGranted)?.let { s ->
        NoAccessDialog(
            resourceUri = s.resourceUri,
            ownerWebId = s.ownerWebId,
            onAsk = {
                s.ownerWebId?.let { owner ->
                    viewModel.requestAccess(
                        s.resourceUri,
                        owner
                    )
                }
            },
            onClose = { viewModel.resetToScanning() },
        )
    }
}

@Composable
private fun ScannerContent(
    onScanned: (String) -> Unit,
    onPasted: (String) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    var flashOn by remember { mutableStateOf(false) }
    var hasFired by remember { mutableStateOf(false) }
    var showPaste by rememberSaveable { mutableStateOf(false) }

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
                                onScanned(raw)
                            }
                        }
                    }
            }
        }
    }

    LaunchedEffect(flashOn) {
        cameraControlHolder.value?.enableTorch(flashOn)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(24.dp))
                .background(Color.Black),
        ) {
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
                                    onScanned(raw)
                                }
                            },
                        )
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )

            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(200.dp)
                    .border(
                        width = 3.dp,
                        color = Color.White,
                        shape = RoundedCornerShape(20.dp),
                    ),
            )

            androidx.compose.foundation.layout.Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp),
            ) {
                IconButton(onClick = { flashOn = !flashOn }) {
                    Icon(
                        imageVector = if (flashOn) Icons.Filled.FlashOff else Icons.Filled.FlashOn,
                        contentDescription = if (flashOn) stringResource(R.string.flash_off)
                        else stringResource(R.string.flash_on),
                        tint = Color.White,
                    )
                }
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

        Spacer(Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.scan_share_qr_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.scan_share_qr_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        OutlinedButton(onClick = { showPaste = true }) {
            Icon(
                Icons.Outlined.ContentPaste,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.size(8.dp))
            Text(stringResource(R.string.paste_a_link))
        }
    }

    if (showPaste) {
        PasteLinkDialog(
            onDismiss = { showPaste = false },
            onSubmit = { url ->
                showPaste = false
                if (!hasFired) {
                    hasFired = true
                    onPasted(url)
                }
            },
        )
    }
}

@Composable
private fun LoadingBox(label: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(Modifier.height(16.dp))
            Text(label, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun PasteLinkDialog(
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit,
) {
    var input by rememberSaveable { mutableStateOf("") }
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.paste_share_link_title)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.paste_share_link_body),
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.size(12.dp))
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    label = { Text(stringResource(R.string.url_label)) },
                    singleLine = false,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        IconButton(onClick = {
                            scope.launch { clipboard.pasteText()?.let { input = it } }
                        }) {
                            Icon(
                                Icons.Outlined.ContentPaste,
                                contentDescription = stringResource(R.string.paste_from_clipboard),
                            )
                        }
                    },
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = input.isNotBlank(),
                onClick = { onSubmit(input.trim()) },
            ) { Text(stringResource(R.string.add)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun NoAccessDialog(
    resourceUri: String,
    ownerWebId: String?,
    onAsk: () -> Unit,
    onClose: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(R.string.no_access_yet_title)) },
        text = {
            Text(
                if (ownerWebId != null) {
                    stringResource(
                        R.string.no_access_body_with_owner,
                        displayNameForUri(resourceUri),
                        shortenWebId(ownerWebId),
                    )
                } else {
                    stringResource(
                        R.string.no_access_body_no_owner,
                        displayNameForUri(resourceUri),
                    )
                },
            )
        },
        confirmButton = {
            TextButton(onClick = onAsk, enabled = ownerWebId != null) {
                Text(stringResource(R.string.ask_for_access))
            }
        },
        dismissButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.close)) } },
    )
}

@Composable
private fun PermissionPrompt(onRequest: () -> Unit, onClose: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.camera_permission_needed),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onRequest) { Text(stringResource(R.string.grant_permission)) }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onClose) { Text(stringResource(R.string.close)) }
    }
}
