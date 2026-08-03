package com.erfangholami.solidshare.presentation.wallet

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.erfangholami.solidshare.R
import com.erfangholami.solidshare.domain.model.GivenShare
import com.erfangholami.solidshare.presentation.components.ErrorState
import com.erfangholami.solidshare.presentation.components.PreviewSamples
import com.erfangholami.solidshare.presentation.components.RequiresConnectionHint
import com.erfangholami.solidshare.presentation.navigation.PublicProfileRoute
import com.erfangholami.solidshare.presentation.rememberIsOnline
import com.erfangholami.solidshare.presentation.sharing.EntityHeaderRow
import com.erfangholami.solidshare.presentation.sharing.EntityPeopleCard
import com.erfangholami.solidshare.presentation.sharing.EntityRemoveAccessSheet
import com.erfangholami.solidshare.presentation.sharing.EntityShareAddSheet
import com.erfangholami.solidshare.presentation.sharing.ShareLinkPanel
import com.erfangholami.solidshare.presentation.theme.AppTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TicketSharingPage(
    navController: NavController,
    viewModel: TicketShareViewModel,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val isOnline by rememberIsOnline()
    val snackbarHostState = remember { SnackbarHostState() }

    var showAddSheet by rememberSaveable { mutableStateOf(false) }
    var showPublicLink by rememberSaveable { mutableStateOf(false) }
    var removeTarget by remember { mutableStateOf<GivenShare?>(null) }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.share_ticket_title),
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
            when (val s = state) {
                TicketShareViewModel.UiState.Loading ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 220.dp),
                        contentAlignment = Alignment.Center,
                    ) { CircularProgressIndicator() }

                TicketShareViewModel.UiState.Offline ->
                    ErrorState(
                        message = stringResource(R.string.shared_with_offline),
                        icon = Icons.Outlined.CloudOff,
                        retryLabel = stringResource(R.string.retry),
                        onRetry = viewModel::load,
                    )

                is TicketShareViewModel.UiState.Error ->
                    ErrorState(
                        message = s.message,
                        title = stringResource(R.string.manage_load_failed),
                        icon = null,
                        retryLabel = stringResource(R.string.retry),
                        onRetry = viewModel::load,
                    )

                is TicketShareViewModel.UiState.Loaded -> {
                    EntityHeaderRow(
                        icon = Icons.Filled.ConfirmationNumber,
                        title = s.ticket.title,
                        subtitle = stringResource(R.string.entity_kind_ticket),
                    )

                    Spacer(Modifier.height(28.dp))

                    Text(
                        text = stringResource(R.string.people_with_access),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )

                    Spacer(Modifier.height(12.dp))

                    EntityPeopleCard(
                        people = s.people,
                        isOnline = isOnline,
                        onPersonClick = { removeTarget = it },
                        onPersonAvatarClick = {
                            navController.navigate(PublicProfileRoute(it))
                        },
                        onAddPerson = { showAddSheet = true },
                    )

                    Spacer(Modifier.height(24.dp))

                    PublicPassLinkCard(
                        enabled = s.publicEnabled,
                        availability = s.publicAvailability,
                        isOnline = isOnline,
                        onToggle = viewModel::setPublicPassLink,
                        onShowLink = { showPublicLink = true },
                    )
                }
            }
        }
    }

    if (showAddSheet) {
        EntityShareAddSheet(
            onDismiss = { showAddSheet = false },
            submit = viewModel::addPersonSuspend,
            deepLinkFor = viewModel::deepLinkFor,
            bareUrlFor = viewModel::bareUrlFor,
        )
    }

    removeTarget?.let { target ->
        EntityRemoveAccessSheet(
            isOnline = isOnline,
            onRemove = {
                removeTarget = null
                viewModel.revoke(target)
            },
            onDismiss = { removeTarget = null },
        )
    }

    val loaded = state as? TicketShareViewModel.UiState.Loaded
    val artifactUri = loaded?.ticket?.artifactUri
    if (showPublicLink && artifactUri != null) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showPublicLink = false },
            sheetState = sheetState,
        ) {
            ShareLinkPanel(
                resourceUri = artifactUri,
                deepLink = viewModel.deepLinkFor(artifactUri),
                bareUrl = viewModel.bareUrlFor(artifactUri),
                showPublicOption = true,
            )
        }
    }
}

@Composable
internal fun PublicPassLinkCard(
    enabled: Boolean,
    availability: TicketShareViewModel.PublicAvailability,
    isOnline: Boolean,
    onToggle: (Boolean) -> Unit,
    onShowLink: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val available = availability == TicketShareViewModel.PublicAvailability.AVAILABLE
    OutlinedCard(
        shape = RoundedCornerShape(16.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.public_pass_link),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = stringResource(R.string.public_pass_link_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Switch(
                    checked = enabled,
                    onCheckedChange = onToggle,
                    enabled = available && isOnline,
                )
            }
            when (availability) {
                TicketShareViewModel.PublicAvailability.NO_ARTIFACT ->
                    Text(
                        text = stringResource(R.string.public_pass_no_artifact_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )

                TicketShareViewModel.PublicAvailability.PROHIBITED ->
                    Text(
                        text = stringResource(R.string.public_pass_prohibited_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )

                TicketShareViewModel.PublicAvailability.AVAILABLE -> Unit
            }
            RequiresConnectionHint(
                visible = !isOnline,
                modifier = Modifier.padding(top = 8.dp),
            )
            if (enabled) {
                TextButton(onClick = onShowLink) {
                    Text(stringResource(R.string.public_pass_show_link))
                }
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 360, name = "Public link · on")
@Composable
private fun PublicPassLinkCardOnPreview() {
    AppTheme {
        PublicPassLinkCard(
            enabled = true,
            availability = TicketShareViewModel.PublicAvailability.AVAILABLE,
            isOnline = true,
            onToggle = {},
            onShowLink = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Preview(showBackground = true, widthDp = 360, name = "Public link · no artifact")
@Composable
private fun PublicPassLinkCardNoArtifactPreview() {
    AppTheme {
        PublicPassLinkCard(
            enabled = false,
            availability = TicketShareViewModel.PublicAvailability.NO_ARTIFACT,
            isOnline = true,
            onToggle = {},
            onShowLink = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Preview(showBackground = true, widthDp = 360, name = "Public link · prohibited")
@Composable
private fun PublicPassLinkCardProhibitedPreview() {
    AppTheme {
        PublicPassLinkCard(
            enabled = false,
            availability = TicketShareViewModel.PublicAvailability.PROHIBITED,
            isOnline = true,
            onToggle = {},
            onShowLink = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Preview(showBackground = true, widthDp = 360, name = "People card")
@Composable
private fun TicketSharePeoplePreview() {
    AppTheme {
        EntityPeopleCard(
            people = listOf(PreviewSamples.givenShare(name = "ben")),
            isOnline = true,
            onPersonClick = {},
            onPersonAvatarClick = {},
            onAddPerson = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}
