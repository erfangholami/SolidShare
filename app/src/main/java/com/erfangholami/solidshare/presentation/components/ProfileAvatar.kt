package com.erfangholami.solidshare.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.erfangholami.solidshare.presentation.theme.AppTheme
import com.erfangholami.solidshare.presentation.util.initialFor
import com.erfangholami.solidshare.presentation.util.webIdToAvatarColor

@Composable
fun ProfileAvatar(
    webId: String?,
    displayName: String?,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    textStyle: TextStyle? = null,
) {
    val color = if (webId.isNullOrEmpty()) MaterialTheme.colorScheme.surfaceVariant
    else webIdToAvatarColor(webId)
    val initial = initialFor(displayName, webId)

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(color),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initial,
            color = Color.White,
            style = textStyle ?: when {
                size >= 80.dp -> MaterialTheme.typography.displaySmall
                size >= 56.dp -> MaterialTheme.typography.headlineSmall
                size >= 40.dp -> MaterialTheme.typography.titleMedium
                else -> MaterialTheme.typography.labelLarge
            },
        )
    }
}

@Preview(showBackground = true, widthDp = 360, name = "Named")
@Composable
private fun ProfileAvatarNamedPreview() {
    AppTheme {
        Surface {
            ProfileAvatar(
                webId = PreviewSamples.WEB_ID,
                displayName = "Alice Cooper",
                modifier = Modifier.padding(16.dp),
                size = 48.dp,
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 360, name = "Fallback")
@Composable
private fun ProfileAvatarFallbackPreview() {
    AppTheme {
        Surface {
            ProfileAvatar(
                webId = null,
                displayName = null,
                modifier = Modifier.padding(16.dp),
                size = 48.dp,
            )
        }
    }
}
