package com.erfangholami.solidshare.presentation.sharing

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import com.erfangholami.solidshare.R
import com.erfangholami.solidshare.presentation.navigation.PublicProfileRoute
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import kotlinx.coroutines.launch

@Composable
fun ScanQrPage(navController: NavController) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val notAWebIdMessage = stringResource(R.string.qr_not_a_webid)
    val showMessage: (String) -> Unit = { msg ->
        scope.launch { snackbarHostState.showSnackbar(msg) }
    }
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

    Box(modifier = Modifier
        .fillMaxSize()
        .background(Color.Black)) {
        if (hasPermission) {
            ScannerContent(
                navController = navController,
                onResult = { raw ->
                    if (looksLikeWebId(raw)) {
                        navController.navigate(PublicProfileRoute(webId = raw))
                    } else {
                        showMessage(notAWebIdMessage)
                    }
                },
                onMessage = showMessage,
            )
        } else {
            PermissionState(
                onRequest = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                onClose = { navController.popBackStack() },
            )
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun ScannerContent(
    navController: NavController,
    onResult: (String) -> Unit,
    onMessage: (String) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val notFoundInImageMessage = stringResource(R.string.qr_not_found_in_image)
    var flashOn by remember { mutableStateOf(false) }
    var hasNavigated by remember { mutableStateOf(false) }

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

    val cameraControlHolder = remember { mutableStateOf<androidx.camera.core.CameraControl?>(null) }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                processImageUri(context, uri, scanner) { result ->
                    if (result != null && !hasNavigated) {
                        hasNavigated = true
                        onResult(result)
                    } else if (result == null) {
                        onMessage(notFoundInImageMessage)
                    }
                }
            }
        }
    }

    LaunchedEffect(flashOn) {
        cameraControlHolder.value?.enableTorch(flashOn)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).also { previewView ->
                    bindCamera(
                        context = ctx,
                        lifecycleOwner = lifecycleOwner,
                        previewView = previewView,
                        scanner = scanner,
                        onCameraControl = { cameraControlHolder.value = it },
                        onScan = { raw ->
                            if (!hasNavigated) {
                                hasNavigated = true
                                onResult(raw)
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
                .size(260.dp)
                .border(
                    width = 3.dp,
                    color = Color.White,
                    shape = RoundedCornerShape(20.dp),
                ),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp, start = 8.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = stringResource(R.string.close),
                    tint = Color.White
                )
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { flashOn = !flashOn }) {
                Icon(
                    imageVector = if (flashOn) Icons.Filled.FlashOff else Icons.Filled.FlashOn,
                    contentDescription = stringResource(
                        if (flashOn) R.string.flash_off else R.string.flash_on,
                    ),
                    tint = Color.White,
                )
            }
            IconButton(
                onClick = {
                    galleryLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                },
            ) {
                Icon(
                    Icons.Outlined.PhotoLibrary,
                    contentDescription = stringResource(R.string.pick_image),
                    tint = Color.White,
                )
            }
        }

        Text(
            text = stringResource(R.string.scan_qr_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 32.dp, vertical = 48.dp),
        )
    }
}

@Composable
private fun PermissionState(
    onRequest: () -> Unit,
    onClose: () -> Unit,
) {
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
            color = Color.White,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onRequest) { Text(stringResource(R.string.grant_permission)) }
        Spacer(Modifier.height(8.dp))
        Button(onClick = onClose) { Text(stringResource(R.string.close)) }
    }
}

private fun looksLikeWebId(raw: String): Boolean {
    val trimmed = raw.trim()
    return trimmed.startsWith("http://", ignoreCase = true)
            || trimmed.startsWith("https://", ignoreCase = true)
}
