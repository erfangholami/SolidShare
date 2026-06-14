package com.erfangholami.solidshare.presentation.sharing

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.outlined.AddCircle
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.erfangholami.solidshare.R
import com.erfangholami.solidshare.domain.model.GivenShare
import com.erfangholami.solidshare.domain.model.ShareMode
import com.erfangholami.solidshare.domain.model.ShareReceiver
import com.erfangholami.solidshare.presentation.components.BadgeAvatar
import com.erfangholami.solidshare.presentation.components.EntityRow
import com.erfangholami.solidshare.presentation.components.ErrorState
import com.erfangholami.solidshare.presentation.components.PreviewSamples
import com.erfangholami.solidshare.presentation.components.ProfileAvatar
import com.erfangholami.solidshare.presentation.navigation.PublicProfileRoute
import com.erfangholami.solidshare.presentation.theme.AppTheme
import com.erfangholami.solidshare.util.formatRelativeTime
import androidx.compose.material3.Surface

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageSharingPage(
    navController: NavController,
    viewModel: ManageSharingViewModel,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var showAddSheet by rememberSaveable { mutableStateOf(false) }
    var sheetTarget by remember { mutableStateOf<GivenShare?>(null) }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.manage_access),
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            ResourceHeaderRow(
                resourceUri = viewModel.resourceUri,
                subtitle = viewModel.resourceSubtitle,
            )

            Spacer(Modifier.height(28.dp))

            Text(
                text = stringResource(R.string.people_with_access),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )

            Spacer(Modifier.height(12.dp))

            OutlinedCard(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                when (val s = state) {
                    ManageSharingViewModel.UiState.Loading ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 220.dp),
                            contentAlignment = Alignment.Center,
                        ) { CircularProgressIndicator() }

                    is ManageSharingViewModel.UiState.Error ->
                        ErrorState(
                            message = s.message,
                            title = stringResource(R.string.manage_load_failed),
                            icon = null,
                            retryLabel = stringResource(R.string.retry),
                            onRetry = viewModel::load,
                        )

                    is ManageSharingViewModel.UiState.Loaded ->
                        Column(modifier = Modifier.padding(vertical = 8.dp)) {
                            s.owner?.let { owner ->
                                OwnerRow(
                                    owner = owner,
                                    onAvatarClick = {
                                        navController.navigate(PublicProfileRoute(owner.webId))
                                    },
                                )
                            }
                            s.shares.forEach { share ->
                                PersonRow(
                                    share = share,
                                    interactive = viewModel.canManage,
                                    onClick = { sheetTarget = share },
                                    onAvatarClick = (share.receiver as? ShareReceiver.WebIdReceiver)
                                        ?.let { r ->
                                            { navController.navigate(PublicProfileRoute(r.webId)) }
                                        },
                                )
                            }
                            if (viewModel.canManage) {
                                AddPeopleButton(onClick = { showAddSheet = true })
                            }
                        }
                }
            }
        }
    }

    if (showAddSheet) {
        CreateShareSheet(
            resourceUri = viewModel.resourceUri,
            onDismiss = { showAddSheet = false },
            submit = { uri, mode, receiver -> viewModel.createShareSuspend(uri, mode, receiver) },
            deepLinkFor = viewModel::deepLinkFor,
            bareUrlFor = viewModel::bareUrlFor,
        )
    }

    sheetTarget?.let { target ->
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { sheetTarget = null },
            sheetState = sheetState,
        ) {
            AccessModeSheetContent(
                current = target.mode,
                onSelect = { mode ->
                    sheetTarget = null
                    viewModel.changeMode(target, mode)
                },
                onRemove = {
                    sheetTarget = null
                    viewModel.revoke(target)
                },
            )
        }
    }
}

@Composable
private fun OwnerRow(
    owner: ManageSharingViewModel.OwnerInfo,
    onAvatarClick: () -> Unit,
) {
    EntityRow(
        title = stringResource(
            R.string.manage_you_suffix,
            owner.name ?: shortenWebId(owner.webId),
        ),
        subtitle = owner.name?.let { shortenWebId(owner.webId) },
        leading = {
            ProfileAvatar(
                webId = owner.webId,
                displayName = owner.name,
                size = 40.dp,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onAvatarClick),
            )
        },
        trailing = {
            Text(
                text = stringResource(R.string.manage_owner),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    )
}

@Composable
private fun PersonRow(
    share: GivenShare,
    interactive: Boolean,
    onClick: () -> Unit,
    onAvatarClick: (() -> Unit)?,
) {
    val timeText = share.createdAt
        ?.let { formatRelativeTime(it) }
        ?.let { stringResource(R.string.access_granted_relative, it) }
    EntityRow(
        title = describeReceiver(share.receiver),
        subtitle = timeText,
        leading = {
            ReceiverAvatar(
                receiver = share.receiver,
                modifier = if (onAvatarClick != null) {
                    Modifier
                        .clip(CircleShape)
                        .clickable(onClick = onAvatarClick)
                } else {
                    Modifier
                },
            )
        },
        trailing = { AccessControl(mode = share.mode, interactive = interactive, onClick = onClick) },
    )
}

@Composable
private fun AccessControl(
    mode: ShareMode,
    interactive: Boolean,
    onClick: () -> Unit,
) {
    if (interactive) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onClick)
                .padding(start = 10.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = labelFor(mode),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            Icon(
                imageVector = Icons.Filled.ArrowDropDown,
                contentDescription = stringResource(R.string.change_access),
                modifier = Modifier.size(20.dp),
            )
        }
    } else {
        Text(
            text = labelFor(mode),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AddPeopleButton(onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        shape = CircleShape,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(stringResource(R.string.add_people))
        Spacer(Modifier.width(8.dp))
        Icon(
            imageVector = Icons.Outlined.AddCircle,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun ReceiverAvatar(receiver: ShareReceiver, modifier: Modifier = Modifier) {
    when (receiver) {
        is ShareReceiver.WebIdReceiver ->
            ProfileAvatar(
                webId = receiver.webId,
                displayName = null,
                size = 40.dp,
                modifier = modifier,
            )

        is ShareReceiver.GroupReceiver -> BadgeAvatar(Icons.Filled.Group, modifier)
        ShareReceiver.Public -> BadgeAvatar(Icons.Filled.Public, modifier)
    }
}

@Preview(name = "OwnerRow", showBackground = true, widthDp = 360)
@Composable
private fun OwnerRowPreview() {
    AppTheme {
        Surface {
            OwnerRow(
                owner = ManageSharingViewModel.OwnerInfo(
                    webId = PreviewSamples.WEB_ID,
                    name = "Alice Cooper",
                ),
                onAvatarClick = {},
            )
        }
    }
}

@Preview(name = "PersonRow", showBackground = true, widthDp = 360)
@Composable
private fun PersonRowPreview() {
    AppTheme {
        Surface {
            PersonRow(
                share = PreviewSamples.givenShare(name = "ben", mode = ShareMode.WRITE),
                interactive = true,
                onClick = {},
                onAvatarClick = {},
            )
        }
    }
}

@Preview(name = "AccessControl · interactive", showBackground = true, widthDp = 360)
@Composable
private fun AccessControlInteractivePreview() {
    AppTheme {
        Surface {
            AccessControl(mode = ShareMode.READ, interactive = true, onClick = {})
        }
    }
}

@Preview(name = "AccessControl · read-only", showBackground = true, widthDp = 360)
@Composable
private fun AccessControlReadOnlyPreview() {
    AppTheme {
        Surface {
            AccessControl(mode = ShareMode.READ, interactive = false, onClick = {})
        }
    }
}

@Preview(name = "AddPeopleButton", showBackground = true, widthDp = 360)
@Composable
private fun AddPeopleButtonPreview() {
    AppTheme {
        Surface {
            AddPeopleButton(onClick = {})
        }
    }
}

@Preview(name = "ReceiverAvatar", showBackground = true, widthDp = 360)
@Composable
private fun ReceiverAvatarPreview() {
    AppTheme {
        Surface {
            Column(modifier = Modifier.padding(16.dp)) {
                ReceiverAvatar(
                    receiver = ShareReceiver.WebIdReceiver(PreviewSamples.WEB_ID),
                )
            }
        }
    }
}
