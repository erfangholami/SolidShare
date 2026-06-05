package com.erfangholami.solidshare.presentation.sharing

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.erfangholami.solidshare.R
import com.erfangholami.solidshare.presentation.main.ResourceTile
import com.erfangholami.solidshare.presentation.navigation.ScanRoute

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfirmAccessPage(
    navController: NavController,
    viewModel: ConfirmAccessViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val addedMsg = stringResource(R.string.added_to_shares)
    val requestSentMsg = stringResource(R.string.access_request_sent)

    LaunchedEffect(state) {
        when (state) {
            is ConfirmAccessViewModel.State.Added -> finishToCaller(navController, addedMsg)
            is ConfirmAccessViewModel.State.RequestSent -> finishToCaller(navController, requestSentMsg)
            else -> Unit
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.confirm_access_title),
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.close))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ResourceCard(
                resourceUri = viewModel.resourceUri,
                ownerWebId = viewModel.ownerWebId,
            )

            Spacer(Modifier.height(24.dp))

            when (val s = state) {
                ConfirmAccessViewModel.State.Checking ->
                    StatusRow(loading = true, text = stringResource(R.string.checking_access))

                ConfirmAccessViewModel.State.Owned ->
                    StatusRow(
                        loading = false,
                        text = stringResource(R.string.you_are_owner_title),
                        icon = StatusIcon.POSITIVE,
                    )

                ConfirmAccessViewModel.State.Adding,
                ConfirmAccessViewModel.State.Added,
                ConfirmAccessViewModel.State.RequestSent ->
                    StatusRow(loading = true, text = stringResource(R.string.checking_access))

                ConfirmAccessViewModel.State.HasAccess -> {
                    StatusRow(
                        loading = false,
                        text = stringResource(R.string.you_have_access),
                        icon = StatusIcon.POSITIVE,
                    )
                    Spacer(Modifier.weight(1f))
                    Button(
                        onClick = viewModel::addToShares,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.add_to_my_shares)) }
                    TextButton(onClick = { navController.popBackStack() }) {
                        Text(stringResource(R.string.not_now))
                    }
                }

                is ConfirmAccessViewModel.State.NoAccess -> {
                    StatusRow(
                        loading = false,
                        text = stringResource(R.string.no_access_yet_title),
                        icon = StatusIcon.NEGATIVE,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = if (s.ownerWebId != null) {
                            stringResource(
                                R.string.no_access_body_with_owner,
                                displayNameForUri(viewModel.resourceUri),
                                shortenWebId(s.ownerWebId),
                            )
                        } else {
                            stringResource(
                                R.string.no_access_body_no_owner,
                                displayNameForUri(viewModel.resourceUri),
                            )
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.weight(1f))
                    if (s.ownerWebId != null) {
                        Button(
                            onClick = { viewModel.requestAccess(s.ownerWebId) },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(stringResource(R.string.ask_for_access)) }
                    }
                    TextButton(onClick = { navController.popBackStack() }) {
                        Text(stringResource(R.string.not_now))
                    }
                }

                is ConfirmAccessViewModel.State.Failure -> {
                    StatusRow(
                        loading = false,
                        text = s.message,
                        icon = StatusIcon.NEGATIVE,
                    )
                    Spacer(Modifier.weight(1f))
                    if (s.canRetry) {
                        Button(
                            onClick = viewModel::check,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(stringResource(R.string.retry)) }
                    }
                    TextButton(onClick = { navController.popBackStack() }) {
                        Text(stringResource(R.string.not_now))
                    }
                }
            }
        }
    }

    if (state is ConfirmAccessViewModel.State.Owned) {
        AlertDialog(
            onDismissRequest = { navController.popBackStack() },
            icon = { Icon(Icons.Outlined.CheckCircle, contentDescription = null) },
            title = { Text(stringResource(R.string.you_are_owner_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.you_are_owner_body,
                        displayNameForUri(viewModel.resourceUri),
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = { navController.popBackStack() }) {
                    Text(stringResource(R.string.got_it))
                }
            },
        )
    }
}

private fun finishToCaller(navController: NavController, message: String) {
    navController.popBackStack(ScanRoute, inclusive = true)
    navController.currentBackStackEntry?.savedStateHandle?.set("received_share_msg", message)
}

@Composable
private fun ResourceCard(resourceUri: String, ownerWebId: String?) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ResourceTile(resourceUri = resourceUri)
            Spacer(Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = displayNameForUri(resourceUri),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (ownerWebId != null) {
                    Spacer(Modifier.size(2.dp))
                    Text(
                        text = stringResource(R.string.from_webid, shortenWebId(ownerWebId)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

private enum class StatusIcon { POSITIVE, NEGATIVE }

@Composable
private fun StatusRow(
    loading: Boolean,
    text: String,
    icon: StatusIcon? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(20.dp), contentAlignment = Alignment.Center) {
            if (loading) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            } else if (icon != null) {
                Icon(
                    imageVector = when (icon) {
                        StatusIcon.POSITIVE -> Icons.Outlined.CheckCircle
                        StatusIcon.NEGATIVE -> Icons.Outlined.ErrorOutline
                    },
                    contentDescription = null,
                    tint = when (icon) {
                        StatusIcon.POSITIVE -> MaterialTheme.colorScheme.primary
                        StatusIcon.NEGATIVE -> MaterialTheme.colorScheme.error
                    },
                )
            }
        }
        Spacer(Modifier.size(10.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
