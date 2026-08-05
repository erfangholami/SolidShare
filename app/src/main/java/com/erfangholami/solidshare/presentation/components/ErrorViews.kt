package com.erfangholami.solidshare.presentation.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.erfangholami.solidshare.R
import com.erfangholami.solidshare.domain.error.AppError
import com.erfangholami.solidshare.domain.error.AppOperation
import com.erfangholami.solidshare.domain.error.ErrorAction
import com.erfangholami.solidshare.domain.error.UiError
import com.erfangholami.solidshare.presentation.theme.AppTheme

/**
 * The inline banner for a [UiError] that leaves the screen usable — a refresh that failed, a
 * write that was refused. Renders the headline, the explanation, and whichever recovery the
 * error carries, so no screen decides for itself what a given failure offers.
 *
 * A handler left `null` suppresses that action even when the error carries it, for screens that
 * genuinely cannot perform it.
 */
@Composable
fun UiErrorBanner(
    error: UiError,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
    onSignIn: (() -> Unit)? = null,
    onRequestAccess: ((ErrorAction.RequestAccess) -> Unit)? = null,
) {
    DismissibleBanner(
        message = error.message,
        onDismiss = onDismiss,
        modifier = modifier,
        title = error.title,
        tone = BannerTone.ERROR,
        action = when (val action = error.action) {
            ErrorAction.Retry -> onRetry?.let {
                { TextButton(onClick = it) { Text(stringResource(R.string.retry)) } }
            }

            ErrorAction.SignIn -> onSignIn?.let {
                { TextButton(onClick = it) { Text(stringResource(R.string.error_action_sign_in)) } }
            }

            is ErrorAction.RequestAccess -> onRequestAccess?.let { handle ->
                {
                    TextButton(
                        onClick = { handle(action) },
                        enabled = action.ownerWebId != null,
                    ) { Text(stringResource(R.string.request_access)) }
                }
            }

            null -> null
        },
    )
}

/**
 * The full-slot version of [UiErrorBanner], for when the failure left nothing to show — a list
 * that never loaded, a detail page whose subject could not be read.
 */
@Composable
fun UiErrorState(
    error: UiError,
    modifier: Modifier = Modifier,
    icon: ImageVector? = Icons.Filled.Warning,
    iconSize: Dp = 56.dp,
    onRetry: (() -> Unit)? = null,
) {
    val retry = onRetry?.takeIf { error.action == ErrorAction.Retry }
    ErrorState(
        message = error.message,
        modifier = modifier,
        title = error.title,
        icon = icon,
        iconSize = iconSize,
        retryLabel = retry?.let { stringResource(R.string.retry) },
        onRetry = retry,
    )
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun UiErrorBannerRetryPreview() {
    AppTheme {
        UiErrorBanner(
            error = UiError(
                title = "Couldn't load sharing",
                message = "solidcommunity.net took too long to answer. It may be busy — try again in a moment.",
                action = ErrorAction.Retry,
                error = AppError.Timeout("solidcommunity.net"),
                operation = AppOperation.LOAD_SHARES,
            ),
            onDismiss = {},
            onRetry = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun UiErrorBannerRequestAccessPreview() {
    AppTheme {
        UiErrorBanner(
            error = UiError(
                title = "Couldn't add this share",
                message = "You don't have access to this yet. Alice can share it with you.",
                action = ErrorAction.RequestAccess(PreviewSamples.RESOURCE, PreviewSamples.OWNER_WEB_ID),
                error = AppError.PermissionDenied(PreviewSamples.RESOURCE, PreviewSamples.OWNER_WEB_ID),
                operation = AppOperation.ADD_RECEIVED_SHARE,
            ),
            onDismiss = {},
            onRequestAccess = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun UiErrorStatePreview() {
    AppTheme {
        UiErrorState(
            error = UiError(
                title = "Couldn't load your files",
                message = "You're offline. This will work again as soon as you reconnect.",
                action = ErrorAction.Retry,
                error = AppError.Offline,
                operation = AppOperation.LOAD_FILES,
            ),
            onRetry = {},
        )
    }
}
