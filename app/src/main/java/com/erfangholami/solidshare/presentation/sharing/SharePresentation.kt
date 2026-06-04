package com.erfangholami.solidshare.presentation.sharing

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.erfangholami.solidshare.R
import com.erfangholami.solidshare.domain.model.ResourceType
import com.erfangholami.solidshare.domain.model.ShareMode
import com.erfangholami.solidshare.domain.model.ShareReceiver
import com.erfangholami.solidshare.domain.model.getResourceType
import java.net.URLDecoder

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

@Composable
internal fun labelFor(mode: ShareMode): String = when (mode) {
    ShareMode.READ -> stringResource(R.string.share_mode_read)
    ShareMode.APPEND -> stringResource(R.string.share_mode_append)
    ShareMode.WRITE -> stringResource(R.string.share_mode_write)
}

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
