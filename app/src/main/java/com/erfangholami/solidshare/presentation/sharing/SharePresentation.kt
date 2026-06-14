package com.erfangholami.solidshare.presentation.sharing

import androidx.annotation.StringRes
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.NoteAdd
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.erfangholami.solidshare.R
import com.erfangholami.solidshare.domain.model.ResourceType
import com.erfangholami.solidshare.domain.model.ShareMode
import com.erfangholami.solidshare.domain.model.ShareReceiver
import com.erfangholami.solidshare.domain.model.getResourceType
import com.erfangholami.solidshare.presentation.components.PreviewSamples
import com.erfangholami.solidshare.presentation.container.ResourceTypeIcon
import com.erfangholami.solidshare.presentation.theme.AppTheme
import java.net.URLDecoder

@Composable
internal fun ResourceHeaderRow(
    resourceUri: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    iconOverlay: (@Composable BoxScope.() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            ResourceTypeIcon(type = resourceTypeForUri(resourceUri), size = 56.dp)
            iconOverlay?.invoke(this)
        }
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = displayNameForUri(resourceUri),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                modifier = Modifier.basicMarquee(),
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

internal fun displayNameForUri(resourceUri: String): String {
    val sanitized = resourceUri.trimEnd('/')
    val tail = sanitized.substringAfterLast('/')
    val decoded = runCatching { URLDecoder.decode(tail, "UTF-8") }.getOrDefault(tail)
    return decoded.ifBlank { resourceUri.substringAfter("//") }
}

internal fun isContainerUri(resourceUri: String): Boolean = resourceUri.endsWith('/')

private fun extensionForUri(resourceUri: String): String? {
    if (isContainerUri(resourceUri)) return null
    val name = displayNameForUri(resourceUri)
    val dot = name.lastIndexOf('.')
    return if (dot in 1 until name.length - 1) name.substring(dot + 1).lowercase() else null
}

internal fun resourceTypeForUri(resourceUri: String): ResourceType {
    if (isContainerUri(resourceUri)) return ResourceType.FOLDER
    return getResourceType(
        isContainer = false,
        mimeType = null,
        extension = extensionForUri(resourceUri)
    )
}

@StringRes
internal fun shareModeLabelRes(mode: ShareMode): Int = when (mode) {
    ShareMode.READ -> R.string.share_mode_read
    ShareMode.APPEND -> R.string.share_mode_append
    ShareMode.WRITE -> R.string.share_mode_write
}

internal fun iconFor(mode: ShareMode): ImageVector = when (mode) {
    ShareMode.READ -> Icons.Outlined.Visibility
    ShareMode.APPEND -> Icons.AutoMirrored.Outlined.NoteAdd
    ShareMode.WRITE -> Icons.Outlined.Edit
}

@Composable
internal fun labelFor(mode: ShareMode): String = stringResource(shareModeLabelRes(mode))

@Composable
internal fun describeReceiver(receiver: ShareReceiver): String = when (receiver) {
    is ShareReceiver.WebIdReceiver -> shortenWebId(receiver.webId)
    is ShareReceiver.GroupReceiver ->
        stringResource(R.string.receiver_group_prefix, shortenWebId(receiver.groupUri))
    is ShareReceiver.Public -> stringResource(R.string.receiver_anyone_with_link)
}

internal fun ShareReceiver.subjectKey(): String = when (this) {
    is ShareReceiver.WebIdReceiver -> webId
    is ShareReceiver.GroupReceiver -> groupUri
    is ShareReceiver.Public -> "public"
}

internal fun shortenWebId(webId: String): String {
    val noScheme = webId.substringAfter("//")
    return noScheme.substringBefore('#').trimEnd('/')
}

internal fun hostFor(webId: String): String {
    val noScheme = webId.substringAfter("//")
    return noScheme.substringBefore('/')
        .substringBefore(':')
}

@Preview(name = "ResourceHeaderRow", showBackground = true, widthDp = 360)
@Composable
private fun ResourceHeaderRowPreview() {
    AppTheme {
        Surface {
            Column(modifier = Modifier.padding(16.dp)) {
                ResourceHeaderRow(
                    resourceUri = PreviewSamples.RESOURCE,
                    subtitle = "Shared by Alice",
                )
            }
        }
    }
}
