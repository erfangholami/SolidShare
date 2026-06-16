package com.erfangholami.solidshare.presentation.permissions

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.erfangholami.solidshare.presentation.components.PermissionRationaleDialog
import com.erfangholami.solidshare.presentation.components.PermissionSettingsDialog

@Stable
class PermissionGate internal constructor(
    val isGranted: Boolean,
    private val onRun: (action: () -> Unit) -> Unit,
) {
    fun run(action: () -> Unit) {
        onRun(action)
    }
}

@Composable
fun rememberPermissionGate(
    permission: String,
    required: Boolean,
    rationaleTitle: String,
    rationaleText: String,
    settingsText: String,
    onDenied: (() -> Unit)? = null,
    onComplete: (() -> Unit)? = null,
): PermissionGate {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }

    var granted by remember {
        mutableStateOf(!required || isPermissionGranted(context, permission))
    }
    var permanentlyDenied by remember { mutableStateOf(false) }
    var showRationale by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { result ->
        granted = result
        if (result) {
            val action = pendingAction
            pendingAction = null
            action?.invoke()
        } else {
            pendingAction = null
            permanentlyDenied = activity != null &&
                    !ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
            onDenied?.invoke()
        }
        onComplete?.invoke()
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && required) {
                val current = isPermissionGranted(context, permission)
                granted = current
                if (current) permanentlyDenied = false
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (showRationale) {
        PermissionRationaleDialog(
            title = rationaleTitle,
            text = rationaleText,
            onConfirm = {
                showRationale = false
                launcher.launch(permission)
            },
            onDismiss = {
                showRationale = false
                pendingAction = null
                onComplete?.invoke()
            },
        )
    }

    if (showSettings) {
        PermissionSettingsDialog(
            title = rationaleTitle,
            text = settingsText,
            onOpenSettings = {
                showSettings = false
                activity?.openAppSettings()
                onComplete?.invoke()
            },
            onDismiss = {
                showSettings = false
                pendingAction = null
                val current = isPermissionGranted(context, permission)
                granted = current
                if (current) permanentlyDenied = false
                onComplete?.invoke()
            },
        )
    }

    val run: (() -> Unit) -> Unit = { action ->
        when {
            !required || granted -> action()
            permanentlyDenied -> {
                pendingAction = action
                showSettings = true
            }

            else -> {
                pendingAction = action
                showRationale = true
            }
        }
    }

    return PermissionGate(isGranted = granted, onRun = run)
}
