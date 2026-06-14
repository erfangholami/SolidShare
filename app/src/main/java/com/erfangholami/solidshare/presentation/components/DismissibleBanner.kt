package com.erfangholami.solidshare.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.erfangholami.solidshare.R
import com.erfangholami.solidshare.presentation.theme.AppTheme

enum class BannerTone { ERROR, INFO }

@Composable
fun DismissibleBanner(
    message: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    tone: BannerTone = BannerTone.ERROR,
    action: (@Composable RowScope.() -> Unit)? = null,
) {
    val container = when (tone) {
        BannerTone.ERROR -> MaterialTheme.colorScheme.errorContainer
        BannerTone.INFO -> MaterialTheme.colorScheme.secondaryContainer
    }
    val content = when (tone) {
        BannerTone.ERROR -> MaterialTheme.colorScheme.onErrorContainer
        BannerTone.INFO -> MaterialTheme.colorScheme.onSecondaryContainer
    }
    Surface(
        color = container,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(12.dp)),
    ) {
        Column(modifier = Modifier.padding(start = 12.dp, top = 8.dp, end = 4.dp, bottom = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = content,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = stringResource(R.string.dismiss),
                        tint = content,
                    )
                }
            }
            if (action != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    content = action,
                )
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun BannerErrorWithActionPreview() {
    AppTheme {
        DismissibleBanner(
            message = "Couldn't load your shares.",
            onDismiss = {},
            tone = BannerTone.ERROR,
            action = { TextButton(onClick = {}) { Text("Retry") } },
        )
    }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun BannerInfoPreview() {
    AppTheme {
        DismissibleBanner(message = "Link copied.", onDismiss = {}, tone = BannerTone.INFO)
    }
}
