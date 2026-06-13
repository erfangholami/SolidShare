package com.erfangholami.solidshare.presentation.container

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.erfangholami.solidshare.R
import com.erfangholami.solidshare.domain.model.ContainerItem
import com.erfangholami.solidshare.domain.model.ResourceAccess
import com.erfangholami.solidshare.presentation.navigation.ManageSharingRoute
import com.erfangholami.solidshare.presentation.sharing.SharedAccessGroups
import com.erfangholami.solidshare.presentation.sharing.SharedWithHeader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResourceDetailsPage(
    navController: NavController,
    viewModel: ResourceDetailsViewModel,
) {
    val item by viewModel.itemState.collectAsStateWithLifecycle()
    val sharesState by viewModel.sharesState.collectAsStateWithLifecycle()

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.loadShares()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = item.name,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
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
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ResourcePreview(item = item)

            DetailsCard(item = item)

            if (viewModel.canManageSharing) {
                SharedWithCard(
                    state = sharesState,
                    onManage = {
                        navController.navigate(
                            ManageSharingRoute(resourceUri = item.identifier, canManage = true),
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun ResourcePreview(item: ContainerItem) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .background(
                item.resourceType.tint.copy(alpha = 0.12f),
                RoundedCornerShape(16.dp),
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = item.resourceType.icon,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = item.resourceType.tint,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = item.shortTypeLabel(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DetailsCard(item: ContainerItem) {
    SectionCard {
        Text(
            text = stringResource(R.string.details),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(8.dp))
        DetailRow(stringResource(R.string.details_name), item.name)
        DetailRow(stringResource(R.string.details_type), item.typeLabel())
        item.sizeLabel()?.let { DetailRow(stringResource(R.string.details_size), it) }
        item.createdLabel()?.let { DetailRow(stringResource(R.string.details_created), it) }
        item.modifiedLabel()?.let { DetailRow(stringResource(R.string.details_modified), it) }
        item.parentContainerName()?.let { DetailRow(stringResource(R.string.details_location), it) }
        DetailRow(stringResource(R.string.details_access), accessLabel(item.access))
        DetailRow(
            label = stringResource(R.string.details_url),
            value = item.identifier,
            valueMaxLines = 3,
        )
    }
}

@Composable
private fun SharedWithCard(
    state: ResourceDetailsViewModel.SharesState,
    onManage: () -> Unit,
) {
    SectionCard {
        SharedWithHeader(onManage = onManage)
        Spacer(Modifier.height(10.dp))
        when (state) {
            ResourceDetailsViewModel.SharesState.Loading ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = stringResource(R.string.shared_with_loading),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

            is ResourceDetailsViewModel.SharesState.Error ->
                Text(
                    text = state.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )

            is ResourceDetailsViewModel.SharesState.Loaded ->
                if (state.shares.isEmpty()) {
                    Text(
                        text = stringResource(R.string.shared_with_none),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    SharedAccessGroups(shares = state.shares)
                }
        }
    }
}

@Composable
private fun SectionCard(content: @Composable ColumnScope.() -> Unit) {
    OutlinedCard(
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            content = content,
        )
    }
}

@Composable
private fun DetailRow(label: String, value: String, valueMaxLines: Int = 2) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(104.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = valueMaxLines,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

private fun accessLabel(access: ResourceAccess): String = when {
    access.canControl -> "Owner · full control"
    access.canWrite -> "Read & write"
    access.canAppend -> "Read & append"
    else -> "Read only"
}
