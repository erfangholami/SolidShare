package com.erfangholami.solidshare.presentation.main

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.erfangholami.solidshare.R
import com.erfangholami.solidshare.domain.model.ShareMode
import com.erfangholami.solidshare.presentation.components.AccountSwitcherCircle
import com.erfangholami.solidshare.presentation.container.ResourceTypeIcon
import com.erfangholami.solidshare.presentation.navigation.ManageSharingRoute
import com.erfangholami.solidshare.presentation.navigation.NotificationsRoute
import com.erfangholami.solidshare.presentation.navigation.PublicProfileRoute
import com.erfangholami.solidshare.presentation.navigation.SharedContainerRoute
import com.erfangholami.solidshare.presentation.notifications.TopBarNotificationBell
import com.erfangholami.solidshare.presentation.sharing.ShareLinkPanel
import com.erfangholami.solidshare.presentation.sharing.displayNameForUri
import com.erfangholami.solidshare.presentation.sharing.iconFor
import com.erfangholami.solidshare.presentation.sharing.labelFor
import com.erfangholami.solidshare.presentation.sharing.resourceTypeForUri
import com.erfangholami.solidshare.presentation.sharing.shortenWebId

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
    val openWithChooser = stringResource(R.string.open_with_chooser)
    val noAppMsg = stringResource(R.string.no_app_to_open)

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
                title = link.title,
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
                    NoticeBanner(
                        text = state.notice.orEmpty(),
                        onDismiss = viewModel::clearNotice,
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
                                    title = displayNameForUri(resourceUri),
                                    deepLink = viewModel.deepLinkFor(resourceUri),
                                    bareUrl = viewModel.bareUrlFor(resourceUri),
                                )
                            },
                            onManage = { resourceUri ->
                                navController.navigate(
                                    ManageSharingRoute(resourceUri = resourceUri, canManage = true),
                                )
                            },
                        )

                        1 -> ReceivedList(
                            shares = state.received,
                            reshareable = state.reshareable,
                            onOpen = { viewModel.openReceivedShare(it) },
                            onRemove = { viewModel.removeReceivedShare(it) },
                            onReshare = { viewModel.reshareReceivedShare(it) },
                            onOpenOwner = {
                                navController.navigate(PublicProfileRoute(it.ownerWebId))
                            },
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
            onRequestAccess = { viewModel.confirmRequestAccessForLostShare() },
            onDismiss = { viewModel.dismissLostAccessShare() },
        )
    }

    noAccessShare?.let { target ->
        NoAccessDialog(
            resourceName = displayNameForUri(target.resourceUri),
            ownerWebId = target.ownerWebId,
            onRequestAccess = { viewModel.confirmRequestAccessForNoAccess() },
            onDismiss = { viewModel.dismissNoAccessShare() },
        )
    }

    ownedResource?.let { uri ->
        OwnerDialog(
            resourceName = displayNameForUri(uri),
            onDismiss = { viewModel.dismissOwnedResource() },
        )
    }

    qrSharePayload?.let { payload ->
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { qrSharePayload = null },
            sheetState = sheetState,
        ) {
            ShareLinkPanel(
                title = payload.title,
                deepLink = payload.deepLink,
                bareUrl = payload.bareUrl,
                onClose = { qrSharePayload = null },
            )
        }
    }
}

internal data class QrPayload(
    val title: String,
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
internal fun ModeChip(mode: ShareMode) {
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
            disabledContainerColor = MaterialTheme.colorScheme.secondaryContainer,
            disabledLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
            disabledLeadingIconContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
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
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(12.dp)),
    ) {
        Column(modifier = Modifier.padding(start = 12.dp, top = 8.dp, end = 4.dp, bottom = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = error.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = stringResource(R.string.dismiss),
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
            when (val action = error.action) {
                ShareViewModel.ErrorAction.Retry ->
                    TextButton(
                        onClick = onRetry,
                        modifier = Modifier.align(Alignment.End),
                    ) { Text(stringResource(R.string.retry)) }

                is ShareViewModel.ErrorAction.RequestAccess ->
                    TextButton(
                        onClick = { onRequestAccess(action) },
                        enabled = action.ownerWebId != null,
                        modifier = Modifier.align(Alignment.End),
                    ) { Text(stringResource(R.string.request_access)) }

                null -> Unit
            }
        }
    }
}

@Composable
private fun NoticeBanner(text: String, onDismiss: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(12.dp)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onDismiss) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = stringResource(R.string.dismiss),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
    }
}

@Composable
internal fun EmptyState(
    icon: ImageVector,
    title: String,
    subtitle: String,
    actionLabel: String?,
    onAction: (() -> Unit)?,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(36.dp),
            )
        }
        Spacer(Modifier.size(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.size(4.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.size(16.dp))
            FilledTonalButton(onClick = onAction) { Text(actionLabel) }
        }
    }
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
