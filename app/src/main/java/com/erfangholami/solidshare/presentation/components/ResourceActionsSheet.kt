package com.erfangholami.solidshare.presentation.components

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DownloadForOffline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.ManageAccounts
import androidx.compose.material.icons.outlined.PersonRemove
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Share as ShareOutlined
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.erfangholami.solidshare.R
import com.erfangholami.solidshare.presentation.sharing.ResourceHeaderRow
import kotlinx.coroutines.launch

enum class ResourceAction(
    @param:StringRes val labelRes: Int,
    val icon: ImageVector,
    val destructive: Boolean = false,
) {
    SHARE(R.string.share, Icons.Filled.Share),
    MANAGE_ACCESS(R.string.manage_access, Icons.Outlined.ManageAccounts),
    DUPLICATE(R.string.duplicate, Icons.Outlined.ContentCopy),
    DOWNLOAD(R.string.download_to_device, Icons.Filled.Download),
    MAKE_OFFLINE(R.string.make_available_offline, Icons.Outlined.DownloadForOffline),
    COPY_LINK(R.string.copy_link, Icons.Filled.Link),
    OPEN_IN(R.string.open_in, Icons.AutoMirrored.Filled.OpenInNew),
    INFO(R.string.info, Icons.Outlined.Info),
    SHOW_SHARE_LINK(R.string.show_share_link, Icons.Outlined.QrCodeScanner),
    RESHARE(R.string.reshare, Icons.Outlined.ShareOutlined),
    REMOVE_FROM_LIST(R.string.remove_from_list, Icons.Outlined.PersonRemove),
    DELETE(R.string.delete, Icons.Outlined.Delete, destructive = true),
}

object ResourceActions {
    fun ownerPod(isContainer: Boolean): List<ResourceAction> =
        if (isContainer) {
            listOf(
                ResourceAction.SHARE,
                ResourceAction.MANAGE_ACCESS,
                ResourceAction.MAKE_OFFLINE,
                ResourceAction.COPY_LINK,
                ResourceAction.INFO,
                ResourceAction.DELETE,
            )
        } else {
            listOf(
                ResourceAction.SHARE,
                ResourceAction.MANAGE_ACCESS,
                ResourceAction.DUPLICATE,
                ResourceAction.DOWNLOAD,
                ResourceAction.MAKE_OFFLINE,
                ResourceAction.COPY_LINK,
                ResourceAction.OPEN_IN,
                ResourceAction.INFO,
                ResourceAction.DELETE,
            )
        }

    fun sharedFolderChild(isContainer: Boolean, canEdit: Boolean): List<ResourceAction> =
        buildList {
            if (!isContainer) add(ResourceAction.DOWNLOAD)
            add(ResourceAction.COPY_LINK)
            if (!isContainer) add(ResourceAction.OPEN_IN)
            add(ResourceAction.INFO)
            if (canEdit) add(ResourceAction.DELETE)
        }

    val sharedByMe: List<ResourceAction> =
        listOf(ResourceAction.SHOW_SHARE_LINK, ResourceAction.MANAGE_ACCESS)

    fun sharedWithMe(isContainer: Boolean, canEdit: Boolean): List<ResourceAction> =
        buildList {
            add(ResourceAction.REMOVE_FROM_LIST)
            add(ResourceAction.RESHARE)
            if (!isContainer) add(ResourceAction.DOWNLOAD)
            add(ResourceAction.COPY_LINK)
            if (!isContainer) add(ResourceAction.OPEN_IN)
            add(ResourceAction.INFO)
            if (canEdit) add(ResourceAction.DELETE)
        }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResourceActionsSheet(
    resourceUri: String,
    subtitle: String?,
    actions: List<ResourceAction>,
    onDismiss: () -> Unit,
    onAction: (ResourceAction) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()

    fun dismissThen(action: ResourceAction) {
        scope.launch { sheetState.hide() }.invokeOnCompletion {
            if (!sheetState.isVisible) {
                onDismiss()
                onAction(action)
            }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.padding(bottom = 24.dp)) {
            ResourceHeaderRow(
                resourceUri = resourceUri,
                subtitle = subtitle,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            )
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))
            actions.forEachIndexed { index, action ->
                if (action.destructive && (index == 0 || !actions[index - 1].destructive)) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                }
                SheetActionRow(
                    icon = action.icon,
                    label = stringResource(action.labelRes),
                    tint = if (action.destructive) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    onClick = { dismissThen(action) },
                )
            }
        }
    }
}
