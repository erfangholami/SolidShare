package com.erfangholami.solidshare.presentation.sharing

import android.content.Context
import android.util.Size
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.erfangholami.solidshare.domain.model.TicketBarcodeFormat
import java.util.concurrent.Executors

internal fun bindCamera(
    context: Context,
    lifecycleOwner: LifecycleOwner,
    previewView: PreviewView,
    decoder: BarcodeDecoder,
    onCameraControl: (CameraControl) -> Unit,
    onScan: (String, TicketBarcodeFormat) -> Unit,
) {
    val providerFuture = ProcessCameraProvider.getInstance(context)
    providerFuture.addListener({
        val provider = providerFuture.get()
        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }
        val analyzer = ImageAnalysis.Builder()
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setResolutionStrategy(
                        ResolutionStrategy(
                            Size(1280, 720),
                            ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
                        ),
                    )
                    .build(),
            )
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()

        val executor = Executors.newSingleThreadExecutor()
        analyzer.setAnalyzer(executor) { proxy: ImageProxy ->
            processImageProxy(proxy, decoder, onScan)
        }

        try {
            provider.unbindAll()
            val camera = provider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                analyzer,
            )
            onCameraControl(camera.cameraControl)
        } catch (_: Exception) {
        }
    }, ContextCompat.getMainExecutor(context))
}

private fun processImageProxy(
    proxy: ImageProxy,
    decoder: BarcodeDecoder,
    onScan: (String, TicketBarcodeFormat) -> Unit,
) {
    try {
        val hit = decoder.decode(proxy)
        if (hit != null) onScan(hit.value, hit.format)
    } finally {
        proxy.close()
    }
}
