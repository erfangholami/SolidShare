package com.erfangholami.solidshare.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.erfangholami.solidshare.presentation.theme.AppTheme

enum class LoadingLayout { COLUMN, ROW }

@Composable
fun LoadingState(
    modifier: Modifier = Modifier,
    label: String? = null,
    layout: LoadingLayout = LoadingLayout.COLUMN,
) {
    when (layout) {
        LoadingLayout.COLUMN -> Column(
            modifier = modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            CircularProgressIndicator()
            if (label != null) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        LoadingLayout.ROW -> Row(
            modifier = modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            if (label != null) {
                Spacer(Modifier.width(12.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun ErrorState(
    message: String,
    modifier: Modifier = Modifier,
    title: String? = null,
    icon: ImageVector? = Icons.Filled.Warning,
    iconSize: Dp = 56.dp,
    retryLabel: String? = null,
    onRetry: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(iconSize),
                tint = MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.height(16.dp))
        }
        if (title != null) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
        }
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (onRetry != null && retryLabel != null) {
            Spacer(Modifier.height(20.dp))
            Button(onClick = onRetry) { Text(retryLabel) }
        }
    }
}

@Preview(showBackground = true, widthDp = 320)
@Composable
private fun ErrorStateIconPreview() {
    AppTheme { ErrorState(message = "Something went wrong while loading.", retryLabel = "Retry", onRetry = {}) }
}

@Preview(showBackground = true, widthDp = 320)
@Composable
private fun ErrorStateTitlePreview() {
    AppTheme {
        ErrorState(
            title = "Couldn't load",
            message = "Check your connection and try again.",
            icon = null,
            retryLabel = "Retry",
            onRetry = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 320)
@Composable
private fun LoadingColumnPreview() {
    AppTheme { LoadingState(label = "Loading…") }
}

@Preview(showBackground = true, widthDp = 320)
@Composable
private fun LoadingRowPreview() {
    AppTheme { LoadingState(label = "Checking access…", layout = LoadingLayout.ROW) }
}
