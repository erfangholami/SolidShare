package com.erfangholami.solidshare.presentation.sharing

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.erfangholami.solidshare.R
import com.erfangholami.solidshare.domain.model.ShareMode
import com.erfangholami.solidshare.presentation.components.LoadingLayout
import com.erfangholami.solidshare.presentation.components.LoadingState
import com.erfangholami.solidshare.presentation.components.PreviewSamples
import com.erfangholami.solidshare.presentation.components.ProfileAvatar
import com.erfangholami.solidshare.presentation.navigation.ScanRoute
import com.erfangholami.solidshare.presentation.theme.AppTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfirmAccessPage(
    navController: NavController,
    viewModel: ConfirmAccessViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val requestedMode by viewModel.requestedMode.collectAsStateWithLifecycle()
    val addedMsg = stringResource(R.string.added_to_shares)
    val requestSentMsg = stringResource(R.string.access_request_sent)

    // Reached either via Scan (pop the scanner too) or directly from a Shared-with-me
    // re-request (no ScanRoute in the stack → just pop this dialog).
    val close: () -> Unit = {
        if (!navController.popBackStack(ScanRoute, inclusive = true)) {
            navController.popBackStack()
        }
    }

    LaunchedEffect(state) {
        when (state) {
            is ConfirmAccessViewModel.State.Added -> finishToCaller(navController, addedMsg)
            is ConfirmAccessViewModel.State.RequestSent -> finishToCaller(navController, requestSentMsg)
            else -> Unit
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = close,
                ),
        )
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                IconButton(
                    onClick = close,
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.close))
                }

                AccessHeader(
                    resourceUri = viewModel.resourceUri,
                    ownerWebId = viewModel.ownerWebId,
                )

                when (val s = state) {
                    ConfirmAccessViewModel.State.Checking,
                    ConfirmAccessViewModel.State.Adding,
                    ConfirmAccessViewModel.State.Added,
                    ConfirmAccessViewModel.State.RequestSent ->
                        LoadingState(
                            label = stringResource(R.string.checking_access),
                            layout = LoadingLayout.ROW,
                        )

                    ConfirmAccessViewModel.State.Owned ->
                        StatusMessage(
                            positive = true,
                            text = stringResource(
                                R.string.you_are_owner_body,
                                displayNameForUri(viewModel.resourceUri),
                            ),
                            title = stringResource(R.string.you_are_owner_title),
                        )

                    ConfirmAccessViewModel.State.HasAccess ->
                        HasAccessContent(onAdd = viewModel::addToShares)

                    is ConfirmAccessViewModel.State.NoAccess ->
                        NoAccessContent(
                            ownerWebId = s.ownerWebId,
                            mode = requestedMode,
                            onModeChange = viewModel::setRequestedMode,
                            onRequest = { s.ownerWebId?.let(viewModel::requestAccess) },
                        )

                    is ConfirmAccessViewModel.State.Failure ->
                        StatusMessage(
                            positive = false,
                            text = s.message,
                            title = null,
                            onRetry = if (s.canRetry) viewModel::check else null,
                        )
                }

                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

private fun finishToCaller(navController: NavController, message: String) {
    if (!navController.popBackStack(ScanRoute, inclusive = true)) {
        navController.popBackStack()
    }
    navController.currentBackStackEntry?.savedStateHandle?.set("received_share_msg", message)
}

@Composable
private fun AccessHeader(resourceUri: String, ownerWebId: String?) {
    ResourceHeaderRow(
        resourceUri = resourceUri,
        subtitle = ownerWebId?.let {
            stringResource(R.string.shared_by_owner, shortenWebId(it))
        },
        iconOverlay = ownerWebId?.let { owner ->
            {
                ProfileAvatar(
                    webId = owner,
                    displayName = null,
                    size = 28.dp,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape),
                )
            }
        },
    )
}

@Composable
private fun NoAccessContent(
    ownerWebId: String?,
    mode: ShareMode,
    onModeChange: (ShareMode) -> Unit,
    onRequest: () -> Unit,
) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(
            imageVector = Icons.Filled.Lock,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                text = stringResource(R.string.access_required_heading),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(
                    R.string.access_required_body,
                    ownerWebId?.let(::shortenWebId) ?: stringResource(R.string.the_owner),
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    AccessModeField(mode = mode, onSelect = onModeChange)

    if (ownerWebId != null) {
        Button(
            onClick = onRequest,
            shape = CircleShape,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
        ) {
            Text(stringResource(R.string.request_access_mode, labelFor(mode)))
        }
    }
}

@Composable
private fun HasAccessContent(onAdd: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = Icons.Outlined.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = stringResource(R.string.you_have_access),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
        )
    }
    Button(
        onClick = onAdd,
        shape = CircleShape,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
    ) {
        Text(stringResource(R.string.add_to_my_shares))
    }
}

@Composable
private fun StatusMessage(
    positive: Boolean,
    text: String,
    title: String?,
    onRetry: (() -> Unit)? = null,
) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(
            imageVector = if (positive) Icons.Outlined.CheckCircle else Icons.Outlined.ErrorOutline,
            contentDescription = null,
            tint = if (positive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column {
            if (title != null) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(6.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    if (onRetry != null) {
        Button(
            onClick = onRetry,
            shape = CircleShape,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
        ) {
            Text(stringResource(R.string.retry))
        }
    }
}

@Composable
private fun AccessModeField(mode: ShareMode, onSelect: (ShareMode) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedTextField(
            value = labelFor(mode),
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.access_mode)) },
            leadingIcon = { Icon(imageVector = iconFor(mode), contentDescription = null) },
            trailingIcon = { Icon(imageVector = Icons.Filled.ArrowDropDown, contentDescription = null) },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(RoundedCornerShape(12.dp))
                .clickable { expanded = true },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            ShareMode.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(labelFor(option)) },
                    leadingIcon = { Icon(imageVector = iconFor(option), contentDescription = null) },
                    onClick = {
                        expanded = false
                        onSelect(option)
                    },
                )
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun AccessHeaderPreview() {
    AppTheme {
        Column(modifier = Modifier.padding(16.dp)) {
            AccessHeader(
                resourceUri = PreviewSamples.RESOURCE,
                ownerWebId = PreviewSamples.OWNER_WEB_ID,
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun NoAccessContentPreview() {
    AppTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            NoAccessContent(
                ownerWebId = PreviewSamples.OWNER_WEB_ID,
                mode = ShareMode.READ,
                onModeChange = {},
                onRequest = {},
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun HasAccessContentPreview() {
    AppTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            HasAccessContent(onAdd = {})
        }
    }
}

@Preview(showBackground = true, widthDp = 360, name = "Status positive")
@Composable
private fun StatusMessagePositivePreview() {
    AppTheme {
        Column(modifier = Modifier.padding(16.dp)) {
            StatusMessage(
                positive = true,
                text = "Access granted",
                title = "All set",
                onRetry = null,
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 360, name = "Status negative")
@Composable
private fun StatusMessageNegativePreview() {
    AppTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            StatusMessage(
                positive = false,
                text = "Something went wrong.",
                title = null,
                onRetry = {},
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun AccessModeFieldPreview() {
    AppTheme {
        Column(modifier = Modifier.padding(16.dp)) {
            AccessModeField(mode = ShareMode.APPEND, onSelect = {})
        }
    }
}
