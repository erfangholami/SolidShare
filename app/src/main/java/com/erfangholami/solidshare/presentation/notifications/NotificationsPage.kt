package com.erfangholami.solidshare.presentation.notifications

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.erfangholami.solidshare.R
import com.erfangholami.solidshare.domain.model.NotificationItem
import com.erfangholami.solidshare.domain.model.NotificationKind
import com.erfangholami.solidshare.domain.model.ShareMode
import com.erfangholami.solidshare.presentation.components.DismissibleBanner
import com.erfangholami.solidshare.presentation.components.PreviewSamples
import com.erfangholami.solidshare.presentation.components.ProfileAvatar
import com.erfangholami.solidshare.presentation.container.icon
import com.erfangholami.solidshare.presentation.container.tint
import com.erfangholami.solidshare.presentation.navigation.SharedContainerRoute
import com.erfangholami.solidshare.presentation.sharing.displayNameForUri
import com.erfangholami.solidshare.presentation.main.ModeChip
import com.erfangholami.solidshare.presentation.sharing.resourceTypeForUri
import com.erfangholami.solidshare.presentation.sharing.shortenWebId
import com.erfangholami.solidshare.presentation.theme.AppTheme
import com.erfangholami.solidshare.util.formatRelativeTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsPage(
    navController: NavController,
    viewModel: NotificationsViewModel,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var rejectTarget by remember { mutableStateOf<NotificationItem?>(null) }

    LaunchedEffect(state.infoMessage) {
        val msg = state.infoMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.clearMessage()
    }

    val context = LocalContext.current
    val openWithChooser = stringResource(R.string.open_with_chooser)
    val noAppMsg = stringResource(R.string.no_app_to_open)
    LaunchedEffect(Unit) {
        viewModel.openEvent.collect { event ->
            when (event) {
                is NotificationsViewModel.OpenEvent.OpenFile -> {
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

                is NotificationsViewModel.OpenEvent.BrowseContainer ->
                    navController.navigate(
                        SharedContainerRoute(
                            containerUrl = event.containerUrl,
                            ownerWebId = event.ownerWebId,
                        ),
                    )
            }
        }
    }

    val unreadCount = state.rows.count { it.isUnread }
    val visibleRows = when (state.selectedTab) {
        NotificationTab.ALL -> state.rows
        NotificationTab.UNREAD -> state.rows.filter { it.isUnread }
        NotificationTab.REQUESTS ->
            state.rows.filter { it.item.kind == NotificationKind.ACCESS_REQUEST }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) { Snackbar(it) } },
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.close),
                        )
                    }
                },
                title = {
                    Text(
                        text = stringResource(R.string.notifications),
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
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                AnimatedVisibility(
                    visible = state.error != null,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    DismissibleBanner(
                        message = state.error.orEmpty(),
                        onDismiss = viewModel::clearError,
                    )
                }

                if (state.rows.isNotEmpty()) {
                    NotificationFilters(
                        selected = state.selectedTab,
                        unreadCount = unreadCount,
                        onSelect = viewModel::selectTab,
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                ) {
                    when {
                        state.isLoading && state.rows.isEmpty() ->
                            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))

                        state.rows.isEmpty() -> EmptyNotifications(NotificationTab.ALL)

                        visibleRows.isEmpty() -> EmptyNotifications(state.selectedTab)

                        else -> LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(vertical = 8.dp),
                        ) {
                            items(visibleRows, key = { it.item.id }) { row ->
                                NotificationRow(
                                    item = row.item,
                                    isUnread = row.isUnread,
                                    alreadyGranted = row.alreadyGranted,
                                    pending = row.item.id in state.pendingUris,
                                    onOpenResource = { viewModel.openResource(row.item) },
                                    onAccept = { viewModel.accept(row.item) },
                                    onReject = { rejectTarget = row.item },
                                    onDismiss = { viewModel.dismiss(row.item) },
                                )
                            }
                        }
                    }
                }
            }

            if (state.isOpening) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f)),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary)
                }
            }
        }
    }

    rejectTarget?.let { item ->
        RejectReasonDialog(
            item = item,
            onDismiss = { rejectTarget = null },
            onConfirm = { reason ->
                viewModel.reject(item, reason)
                rejectTarget = null
            },
        )
    }
}

@Composable
private fun NotificationFilters(
    selected: NotificationTab,
    unreadCount: Int,
    onSelect: (NotificationTab) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterPill(
            label = stringResource(R.string.notifications_tab_all),
            selected = selected == NotificationTab.ALL,
            onClick = { onSelect(NotificationTab.ALL) },
        )
        FilterPill(
            label = if (unreadCount > 0) {
                stringResource(R.string.notifications_tab_unread_count, unreadCount)
            } else {
                stringResource(R.string.notifications_tab_unread)
            },
            selected = selected == NotificationTab.UNREAD,
            onClick = { onSelect(NotificationTab.UNREAD) },
        )
        FilterPill(
            label = stringResource(R.string.notifications_tab_requests),
            selected = selected == NotificationTab.REQUESTS,
            onClick = { onSelect(NotificationTab.REQUESTS) },
        )
    }
}

@Composable
private fun FilterPill(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, maxLines = 1) },
        leadingIcon = if (selected) {
            {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    modifier = Modifier.size(FilterChipDefaults.IconSize),
                )
            }
        } else {
            null
        },
    )
}

@Composable
private fun NotificationRow(
    item: NotificationItem,
    isUnread: Boolean,
    alreadyGranted: Boolean,
    pending: Boolean,
    onOpenResource: () -> Unit,
    onAccept: () -> Unit,
    onReject: () -> Unit,
    onDismiss: () -> Unit,
) {
    val showRequestActions = item.kind == NotificationKind.ACCESS_REQUEST && !alreadyGranted
    Surface(
        color = if (isUnread) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        shape = RoundedCornerShape(16.dp),
        border = if (isUnread) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
        } else {
            null
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                ProfileAvatar(webId = item.counterpartWebId, displayName = null, size = 44.dp)
                Spacer(Modifier.size(12.dp))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(
                        text = titleFor(item),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Normal,
                    )
                    subtitleFor(item)?.let { sub ->
                        Text(
                            text = sub,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = formatRelativeTime(item.publishedAt)
                                ?: stringResource(R.string.notifications_time_unknown),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (item.kind.showsMode()) {
                            item.mode?.let { ModeChip(mode = it) }
                        }
                        if (item.kind == NotificationKind.ACCESS_REQUEST) {
                            AssistChip(
                                onClick = {},
                                enabled = false,
                                label = {
                                    Text(
                                        text = if (alreadyGranted) {
                                            stringResource(R.string.notifications_request_granted)
                                        } else {
                                            stringResource(R.string.notifications_request_pending)
                                        },
                                        maxLines = 1,
                                    )
                                },
                            )
                        }
                    }
                }
                if (!showRequestActions) {
                    DismissButton(pending = pending, onDismiss = onDismiss)
                }
            }

            ResourceChip(
                resourceUri = item.resourceUri,
                onOpen = if (item.kind.opensResource()) onOpenResource else null,
            )

            if (!item.summary.isNullOrBlank()) {
                ReasonLine(item.summary)
            }

            if (showRequestActions) {
                RequestActions(pending = pending, onReject = onReject, onAccept = onAccept)
            }
        }
    }
}

@Composable
private fun titleFor(item: NotificationItem): AnnotatedString {
    val who = shortenWebId(item.counterpartWebId)
    val res = when (item.kind) {
        NotificationKind.ACCESS_OFFER -> R.string.notifications_offer_title
        NotificationKind.ACCESS_UPDATED -> R.string.notifications_updated_title
        NotificationKind.ACCESS_REVOKED -> R.string.notifications_revoked_title
        NotificationKind.REQUEST_REJECTED -> R.string.notifications_rejected_title
        NotificationKind.REQUEST_ACCEPTED -> R.string.notifications_accepted_title
        NotificationKind.ACCESS_REQUEST -> R.string.notifications_request_title
        NotificationKind.DECISION_GRANTED -> R.string.notifications_decision_granted_title
        NotificationKind.DECISION_REJECTED -> R.string.notifications_decision_rejected_title
    }
    val full = stringResource(res, who)
    return buildAnnotatedString {
        val index = full.indexOf(who)
        if (index < 0) {
            append(full)
        } else {
            append(full.substring(0, index))
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(who) }
            append(full.substring(index + who.length))
        }
    }
}

@Composable
private fun subtitleFor(item: NotificationItem): String? = when (item.kind) {
    NotificationKind.ACCESS_REQUEST -> stringResource(R.string.notifications_request_subtitle)
    NotificationKind.REQUEST_ACCEPTED -> stringResource(R.string.notifications_accepted_subtitle)
    NotificationKind.ACCESS_UPDATED -> stringResource(R.string.notifications_updated_subtitle)
    else -> null
}

private fun NotificationKind.showsMode(): Boolean = when (this) {
    NotificationKind.ACCESS_OFFER,
    NotificationKind.ACCESS_UPDATED,
    NotificationKind.ACCESS_REQUEST,
    NotificationKind.REQUEST_ACCEPTED,
    NotificationKind.DECISION_GRANTED,
        -> true

    NotificationKind.ACCESS_REVOKED,
    NotificationKind.REQUEST_REJECTED,
    NotificationKind.DECISION_REJECTED,
        -> false
}

private fun NotificationKind.opensResource(): Boolean = when (this) {
    NotificationKind.ACCESS_OFFER,
    NotificationKind.ACCESS_UPDATED,
    NotificationKind.REQUEST_ACCEPTED,
        -> true

    else -> false
}

@Composable
private fun ResourceChip(resourceUri: String, onOpen: (() -> Unit)?) {
    val type = resourceTypeForUri(resourceUri)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .then(if (onOpen != null) Modifier.clickable(onClick = onOpen) else Modifier)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = type.icon,
            contentDescription = null,
            tint = type.tint,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.size(8.dp))
        Text(
            text = displayNameForUri(resourceUri),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (onOpen != null) {
            Spacer(Modifier.size(8.dp))
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun ReasonLine(text: String) {
    Text(
        text = stringResource(R.string.notifications_summary_quoted, text),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun DismissButton(pending: Boolean, onDismiss: () -> Unit) {
    IconButton(onClick = onDismiss, enabled = !pending) {
        if (pending) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        } else {
            Icon(
                Icons.Outlined.Delete,
                contentDescription = stringResource(R.string.notifications_dismiss),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RequestActions(pending: Boolean, onReject: () -> Unit, onAccept: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedButton(
            onClick = onReject,
            enabled = !pending,
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error,
            ),
        ) {
            Icon(Icons.Default.Close, contentDescription = null)
            Spacer(Modifier.size(6.dp))
            Text(stringResource(R.string.notifications_reject))
        }
        Button(
            onClick = onAccept,
            enabled = !pending,
            modifier = Modifier.weight(1f),
        ) {
            if (pending) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Icon(Icons.Default.Check, contentDescription = null)
                Spacer(Modifier.size(6.dp))
                Text(stringResource(R.string.notifications_accept))
            }
        }
    }
}

@Composable
private fun RejectReasonDialog(
    item: NotificationItem,
    onDismiss: () -> Unit,
    onConfirm: (String?) -> Unit,
) {
    var reason by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.notifications_reject_title)) },
        text = {
            Column {
                Text(
                    stringResource(
                        R.string.notifications_reject_reason_prompt,
                        shortenWebId(item.counterpartWebId),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.size(12.dp))
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text(stringResource(R.string.notifications_reject_reason_hint)) },
                    singleLine = false,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(reason.trim().takeIf { it.isNotEmpty() }) }) {
                Text(stringResource(R.string.notifications_reject))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun EmptyNotifications(tab: NotificationTab) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painter = painterResource(R.drawable.img_notification_empty),
            contentDescription = null,
            modifier = Modifier.size(120.dp),
        )
        Spacer(Modifier.size(20.dp))
        Text(
            text = stringResource(R.string.notifications_empty_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.size(4.dp))
        Text(
            text = stringResource(
                when (tab) {
                    NotificationTab.ALL -> R.string.notifications_empty_subtitle
                    NotificationTab.UNREAD -> R.string.notifications_empty_unread
                    NotificationTab.REQUESTS -> R.string.notifications_empty_requests
                },
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Preview(showBackground = true, widthDp = 360, name = "Filters")
@Composable
private fun NotificationFiltersPreview() {
    AppTheme {
        Surface {
            NotificationFilters(
                selected = NotificationTab.ALL,
                unreadCount = 3,
                onSelect = {},
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 360, name = "Pill Selected")
@Composable
private fun FilterPillSelectedPreview() {
    AppTheme {
        Surface {
            Column(modifier = Modifier.padding(16.dp)) {
                FilterPill(label = "Unread", selected = true, onClick = {})
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 360, name = "Pill Unselected")
@Composable
private fun FilterPillUnselectedPreview() {
    AppTheme {
        Surface {
            Column(modifier = Modifier.padding(16.dp)) {
                FilterPill(label = "Unread", selected = false, onClick = {})
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 360, name = "Row Offer")
@Composable
private fun NotificationRowOfferPreview() {
    AppTheme {
        Surface {
            NotificationRow(
                item = PreviewSamples.notification(
                    kind = NotificationKind.ACCESS_OFFER,
                    mode = ShareMode.READ,
                ),
                isUnread = true,
                alreadyGranted = false,
                pending = false,
                onOpenResource = {},
                onAccept = {},
                onReject = {},
                onDismiss = {},
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 360, name = "Row Request")
@Composable
private fun NotificationRowRequestPreview() {
    AppTheme {
        Surface {
            NotificationRow(
                item = PreviewSamples.notification(
                    kind = NotificationKind.ACCESS_REQUEST,
                    mode = ShareMode.READ,
                    requestUri = "urn:req:1",
                ),
                isUnread = true,
                alreadyGranted = false,
                pending = false,
                onOpenResource = {},
                onAccept = {},
                onReject = {},
                onDismiss = {},
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 360, name = "Row Offer Dark")
@Composable
private fun NotificationRowOfferDarkPreview() {
    AppTheme(isDarkTheme = true) {
        Surface {
            NotificationRow(
                item = PreviewSamples.notification(
                    kind = NotificationKind.ACCESS_OFFER,
                    mode = ShareMode.READ,
                ),
                isUnread = false,
                alreadyGranted = false,
                pending = false,
                onOpenResource = {},
                onAccept = {},
                onReject = {},
                onDismiss = {},
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 360, name = "ResourceChip")
@Composable
private fun ResourceChipPreview() {
    AppTheme {
        Surface {
            Column(modifier = Modifier.padding(16.dp)) {
                ResourceChip(resourceUri = PreviewSamples.RESOURCE, onOpen = {})
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 360, name = "ReasonLine")
@Composable
private fun ReasonLinePreview() {
    AppTheme {
        Surface {
            Column(modifier = Modifier.padding(16.dp)) {
                ReasonLine(text = "Requested because they need to collaborate")
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 360, name = "DismissButton")
@Composable
private fun DismissButtonPreview() {
    AppTheme {
        Surface {
            Column(modifier = Modifier.padding(16.dp)) {
                DismissButton(pending = false, onDismiss = {})
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 360, name = "RequestActions")
@Composable
private fun RequestActionsPreview() {
    AppTheme {
        Surface {
            Column(modifier = Modifier.padding(16.dp)) {
                RequestActions(pending = false, onReject = {}, onAccept = {})
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 360, name = "RejectReasonDialog")
@Composable
private fun RejectReasonDialogPreview() {
    AppTheme {
        RejectReasonDialog(
            item = PreviewSamples.notification(
                kind = NotificationKind.ACCESS_REQUEST,
                requestUri = "urn:req:1",
            ),
            onDismiss = {},
            onConfirm = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 480, name = "Empty All")
@Composable
private fun EmptyNotificationsAllPreview() {
    AppTheme {
        Surface {
            EmptyNotifications(tab = NotificationTab.ALL)
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 480, name = "Empty Unread")
@Composable
private fun EmptyNotificationsUnreadPreview() {
    AppTheme {
        Surface {
            EmptyNotifications(tab = NotificationTab.UNREAD)
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 480, name = "Empty Requests")
@Composable
private fun EmptyNotificationsRequestsPreview() {
    AppTheme {
        Surface {
            EmptyNotifications(tab = NotificationTab.REQUESTS)
        }
    }
}

