package com.erfangholami.solidshare.presentation.main

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.erfangholami.solidshare.R
import com.erfangholami.solidshare.domain.model.ReceivedShare
import com.erfangholami.solidshare.domain.model.ShareMode
import com.erfangholami.solidshare.presentation.components.AccountSwitcherCircle
import com.erfangholami.solidshare.presentation.components.BannerTone
import com.erfangholami.solidshare.presentation.components.DismissibleBanner
import com.erfangholami.solidshare.presentation.container.DeleteResourceDialog
import com.erfangholami.solidshare.presentation.container.ResourceTypeIcon
import com.erfangholami.solidshare.presentation.navigation.ConfirmAccessRoute
import com.erfangholami.solidshare.presentation.navigation.ManageSharingRoute
import com.erfangholami.solidshare.presentation.navigation.NotificationsRoute
import com.erfangholami.solidshare.presentation.navigation.PublicProfileRoute
import com.erfangholami.solidshare.presentation.navigation.ResourceDetailsRoute
import com.erfangholami.solidshare.presentation.navigation.SharedContainerRoute
import com.erfangholami.solidshare.presentation.notifications.TopBarNotificationBell
import com.erfangholami.solidshare.presentation.sharing.ShareLinkPanel
import com.erfangholami.solidshare.presentation.sharing.displayNameForUri
import com.erfangholami.solidshare.presentation.util.copyText
import com.erfangholami.solidshare.presentation.sharing.iconFor
import com.erfangholami.solidshare.presentation.sharing.labelFor
import com.erfangholami.solidshare.presentation.sharing.resourceTypeForUri
import com.erfangholami.solidshare.presentation.sharing.shortenWebId
import com.erfangholami.solidshare.presentation.components.PreviewSamples
import com.erfangholami.solidshare.presentation.theme.AppTheme
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Share(
    navController: NavController,
    viewModel: ShareViewModel,
    onOpenProfile: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val activeProfile by viewModel.activeProfile.collectAsStateWithLifecycle()
    val isOpening by viewModel.isOpening.collectAsStateWithLifecycle()
    val lostAccessShare by viewModel.lostAccessShare.collectAsStateWithLifecycle()
    val noAccessShare by viewModel.noAccessShare.collectAsStateWithLifecycle()
    val ownedResource by viewModel.ownedResource.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val openWithChooser = stringResource(R.string.open_with_chooser)
    val noAppMsg = stringResource(R.string.no_app_to_open)
    val linkCopiedMsg = stringResource(R.string.link_copied)

    var receivedToDelete by remember { mutableStateOf<ReceivedShare?>(null) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.load()
    }

    LaunchedEffect(Unit) {
        viewModel.openEvent.collect { event ->
            when (event) {
                is ShareViewModel.OpenEvent.OpenFile -> {
                    val uri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.provider",
                        event.file,
                    )
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, event.mimeType)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    try {
                        context.startActivity(Intent.createChooser(intent, openWithChooser))
                    } catch (_: ActivityNotFoundException) {
                        viewModel.showNotice(noAppMsg)
                    }
                }

                is ShareViewModel.OpenEvent.BrowseContainer ->
                    navController.navigate(
                        SharedContainerRoute(
                            containerUrl = event.containerUrl,
                            ownerWebId = event.ownerWebId,
                        ),
                    )
            }
        }
    }

    var selectedTab by rememberSaveable { mutableStateOf(0) }
    var qrSharePayload by remember { mutableStateOf<QrPayload?>(null) }
    var overflowOpen by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.reshareLink.collect { link ->
            qrSharePayload = QrPayload(
                resourceUri = link.resourceUri,
                deepLink = link.deepLink,
                bareUrl = link.bareUrl,
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.share),
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                actions = {
                    IconButton(
                        onClick = viewModel::refresh,
                        enabled = !state.isRefreshing,
                    ) {
                        if (state.isRefreshing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(
                                Icons.Outlined.Refresh,
                                contentDescription = stringResource(R.string.refresh),
                            )
                        }
                    }
                    Box {
                        IconButton(
                            onClick = { overflowOpen = true },
                            enabled = !state.isRefreshing,
                        ) {
                            Icon(
                                Icons.Outlined.MoreVert,
                                contentDescription = stringResource(R.string.more_options),
                            )
                        }
                        DropdownMenu(
                            expanded = overflowOpen,
                            onDismissRequest = { overflowOpen = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.rebuild_share_index)) },
                                leadingIcon = { Icon(Icons.Outlined.Autorenew, null) },
                                onClick = {
                                    overflowOpen = false
                                    viewModel.rebuildIndex()
                                },
                            )
                        }
                    }
                    TopBarNotificationBell(
                        onClick = { navController.navigate(NotificationsRoute) },
                    )
                    AccountSwitcherCircle(
                        activeProfile = activeProfile,
                        onClick = onOpenProfile,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
                windowInsets = WindowInsets(0),
            )
        },
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
            ) {
                PrimaryTabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = {
                            Text(
                                stringResource(R.string.shared_by_me_tab, state.given.size),
                                fontWeight = FontWeight.Medium,
                            )
                        },
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = {
                            Text(
                                stringResource(R.string.shared_with_me_tab, state.received.size),
                                fontWeight = FontWeight.Medium,
                            )
                        },
                    )
                }

                AnimatedVisibility(
                    visible = state.error != null,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    state.error?.let { err ->
                        ErrorBanner(
                            error = err,
                            onDismiss = viewModel::clearError,
                            onRetry = viewModel::retry,
                            onRequestAccess = { req ->
                                viewModel.requestAccess(req.resourceUri, req.ownerWebId)
                            },
                        )
                    }
                }

                AnimatedVisibility(
                    visible = state.notice != null,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    DismissibleBanner(
                        message = state.notice.orEmpty(),
                        onDismiss = viewModel::clearNotice,
                        tone = BannerTone.INFO,
                    )
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    val currentTabEmpty =
                        if (selectedTab == 0) state.given.isEmpty() else state.received.isEmpty()
                    if (state.isLoading && currentTabEmpty) {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    } else when (selectedTab) {
                        0 -> GivenList(
                            shares = state.given,
                            onShowQr = { resourceUri ->
                                qrSharePayload = QrPayload(
                                    resourceUri = resourceUri,
                                    deepLink = viewModel.deepLinkFor(resourceUri),
                                    bareUrl = viewModel.bareUrlFor(resourceUri),
                                )
                            },
                            onManage = { resourceUri ->
                                navController.navigate(
                                    ManageSharingRoute(resourceUri = resourceUri, canManage = true),
                                )
                            },
                            loadMeta = { viewModel.resourceMetaItem(it) },
                        )

                        1 -> ReceivedList(
                            shares = state.received,
                            onOpen = { viewModel.openReceivedShare(it) },
                            onRemove = { viewModel.removeReceivedShare(it) },
                            onReshare = { viewModel.reshareReceivedShare(it) },
                            onOpenOwner = {
                                navController.navigate(PublicProfileRoute(it.ownerWebId))
                            },
                            onCopyLink = { share ->
                                scope.launch {
                                    clipboard.copyText(viewModel.bareUrlFor(share.resourceUri))
                                    viewModel.showNotice(linkCopiedMsg)
                                }
                            },
                            onDownload = { viewModel.downloadReceivedShare(it) },
                            onInfo = {
                                navController.navigate(
                                    ResourceDetailsRoute(viewModel.detailsItemFor(it)),
                                )
                            },
                            onDelete = { receivedToDelete = it },
                            loadMeta = { viewModel.resourceMetaItem(it) },
                        )
                    }
                }
            }

            if (isOpening) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary)
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = stringResource(R.string.opening),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                }
            }
        }
    }

    lostAccessShare?.let { share ->
        LostAccessDialog(
            resourceName = displayNameForUri(share.resourceUri),
            ownerWebId = share.ownerWebId,
            onRequestAccess = {
                viewModel.clearLostAccessShare()
                navController.navigate(
                    ConfirmAccessRoute(share.resourceUri, share.ownerWebId),
                )
            },
            onDismiss = { viewModel.dismissLostAccessShare() },
        )
    }

    noAccessShare?.let { target ->
        NoAccessDialog(
            resourceName = displayNameForUri(target.resourceUri),
            ownerWebId = target.ownerWebId,
            onRequestAccess = {
                viewModel.dismissNoAccessShare()
                navController.navigate(
                    ConfirmAccessRoute(target.resourceUri, target.ownerWebId),
                )
            },
            onDismiss = { viewModel.dismissNoAccessShare() },
        )
    }

    ownedResource?.let { uri ->
        OwnerDialog(
            resourceName = displayNameForUri(uri),
            onDismiss = { viewModel.dismissOwnedResource() },
        )
    }

    receivedToDelete?.let { share ->
        DeleteResourceDialog(
            resourceName = displayNameForUri(share.resourceUri),
            onDismiss = { receivedToDelete = null },
            onDelete = {
                receivedToDelete = null
                viewModel.deleteReceivedShare(share)
            },
        )
    }

    qrSharePayload?.let { payload ->
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { qrSharePayload = null },
            sheetState = sheetState,
        ) {
            ShareLinkPanel(
                resourceUri = payload.resourceUri,
                deepLink = payload.deepLink,
                bareUrl = payload.bareUrl,
            )
        }
    }
}

internal data class QrPayload(
    val resourceUri: String,
    val deepLink: String,
    val bareUrl: String,
)

@Composable
private fun LostAccessDialog(
    resourceName: String,
    ownerWebId: String,
    onRequestAccess: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.access_removed_title)) },
        text = {
            Text(
                stringResource(
                    R.string.lost_access_body,
                    resourceName,
                    shortenWebId(ownerWebId),
                ),
            )
        },
        confirmButton = {
            TextButton(onClick = onRequestAccess) { Text(stringResource(R.string.ask_for_access)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.remove)) }
        },
    )
}

@Composable
internal fun ResourceTile(resourceUri: String) {
    ResourceTypeIcon(type = resourceTypeForUri(resourceUri), size = 44.dp)
}

@Composable
internal fun ModeChip(
    mode: ShareMode,
    containerColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSecondaryContainer,
) {
    AssistChip(
        onClick = {},
        enabled = false,
        label = { Text(labelFor(mode), style = MaterialTheme.typography.labelSmall) },
        leadingIcon = {
            Icon(
                imageVector = iconFor(mode),
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
        },
        shape = CircleShape,
        colors = AssistChipDefaults.assistChipColors(
            disabledContainerColor = containerColor,
            disabledLabelColor = contentColor,
            disabledLeadingIconContentColor = contentColor,
        ),
        border = null,
        modifier = Modifier.height(28.dp),
    )
}

@Composable
private fun ErrorBanner(
    error: ShareViewModel.UiError,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    onRequestAccess: (ShareViewModel.ErrorAction.RequestAccess) -> Unit,
) {
    DismissibleBanner(
        message = error.message,
        onDismiss = onDismiss,
        tone = BannerTone.ERROR,
        action = when (val action = error.action) {
            ShareViewModel.ErrorAction.Retry -> {
                { TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) } }
            }

            is ShareViewModel.ErrorAction.RequestAccess -> {
                {
                    TextButton(
                        onClick = { onRequestAccess(action) },
                        enabled = action.ownerWebId != null,
                    ) { Text(stringResource(R.string.request_access)) }
                }
            }

            null -> null
        },
    )
}

@Composable
private fun NoAccessDialog(
    resourceName: String,
    ownerWebId: String?,
    onRequestAccess: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.no_access_yet_title)) },
        text = {
            Text(
                if (ownerWebId != null) {
                    stringResource(
                        R.string.no_access_body_with_owner,
                        resourceName,
                        shortenWebId(ownerWebId),
                    )
                } else {
                    stringResource(
                        R.string.no_access_body_no_owner,
                        resourceName,
                    )
                },
            )
        },
        confirmButton = {
            TextButton(
                onClick = onRequestAccess,
                enabled = ownerWebId != null,
            ) { Text(stringResource(R.string.ask_for_access)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}

@Composable
private fun OwnerDialog(
    resourceName: String,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.you_are_owner_title)) },
        text = { Text(stringResource(R.string.you_are_owner_body, resourceName)) },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.got_it)) }
        },
    )
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun LostAccessDialogPreview() {
    AppTheme {
        LostAccessDialog(
            resourceName = "trip.jpg",
            ownerWebId = PreviewSamples.OWNER_WEB_ID,
            onRequestAccess = {},
            onDismiss = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun ResourceTilePreview() {
    AppTheme {
        Column(modifier = Modifier.padding(16.dp)) {
            ResourceTile(resourceUri = PreviewSamples.RESOURCE)
        }
    }
}

@Preview(showBackground = true, widthDp = 360, name = "ModeChip View")
@Composable
private fun ModeChipReadPreview() {
    AppTheme {
        Column(modifier = Modifier.padding(16.dp)) {
            ModeChip(mode = ShareMode.READ)
        }
    }
}

@Preview(showBackground = true, widthDp = 360, name = "ModeChip Add")
@Composable
private fun ModeChipAppendPreview() {
    AppTheme {
        Column(modifier = Modifier.padding(16.dp)) {
            ModeChip(mode = ShareMode.APPEND)
        }
    }
}

@Preview(showBackground = true, widthDp = 360, name = "ModeChip Edit")
@Composable
private fun ModeChipWritePreview() {
    AppTheme {
        Column(modifier = Modifier.padding(16.dp)) {
            ModeChip(mode = ShareMode.WRITE)
        }
    }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun ErrorBannerPreview() {
    AppTheme {
        ErrorBanner(
            error = ShareViewModel.UiError(
                message = "Couldn't load shares.",
                action = ShareViewModel.ErrorAction.RequestAccess(
                    resourceUri = PreviewSamples.RESOURCE,
                    ownerWebId = PreviewSamples.OWNER_WEB_ID,
                ),
            ),
            onDismiss = {},
            onRetry = {},
            onRequestAccess = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun NoAccessDialogPreview() {
    AppTheme {
        NoAccessDialog(
            resourceName = "trip.jpg",
            ownerWebId = PreviewSamples.OWNER_WEB_ID,
            onRequestAccess = {},
            onDismiss = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun OwnerDialogPreview() {
    AppTheme {
        OwnerDialog(
            resourceName = "trip.jpg",
            onDismiss = {},
        )
    }
}
