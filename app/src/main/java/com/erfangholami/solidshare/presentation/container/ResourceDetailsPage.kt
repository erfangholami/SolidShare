package com.erfangholami.solidshare.presentation.container

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.erfangholami.solidshare.R
import com.erfangholami.solidshare.domain.model.ContainerItem
import com.erfangholami.solidshare.domain.model.ResourceAccess
import com.erfangholami.solidshare.domain.model.ShareMode
import com.erfangholami.solidshare.presentation.components.PreviewSamples
import com.erfangholami.solidshare.presentation.navigation.ManageSharingRoute
import com.erfangholami.solidshare.presentation.sharing.CreateShareSheet
import com.erfangholami.solidshare.presentation.sharing.SharedAccessGroups
import com.erfangholami.solidshare.presentation.sharing.SharedWithHeader
import com.erfangholami.solidshare.presentation.theme.AppTheme
import com.erfangholami.solidshare.presentation.util.copyText
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResourceDetailsPage(
    navController: NavController,
    viewModel: ResourceDetailsViewModel,
) {
    val item by viewModel.itemState.collectAsStateWithLifecycle()
    val sharesState by viewModel.sharesState.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val linkCopiedMsg = stringResource(R.string.link_copied)
    var showShareSheet by rememberSaveable { mutableStateOf(false) }

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
        snackbarHost = { SnackbarHost(snackbarHostState) },
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

            DetailsCard(
                item = item,
                onCopyUrl = {
                    scope.launch {
                        clipboard.copyText(item.identifier)
                        snackbarHostState.showSnackbar(linkCopiedMsg)
                    }
                },
            )

            if (viewModel.canManageSharing) {
                SharedWithCard(
                    state = sharesState,
                    onManage = {
                        navController.navigate(
                            ManageSharingRoute(
                                resourceUri = item.identifier,
                                canManage = true,
                                resourceSubtitle = item.metaSubtitle(),
                            ),
                        )
                    },
                )
            }

            if (viewModel.canShare) {
                Spacer(Modifier.height(4.dp))
                Button(
                    onClick = { showShareSheet = true },
                    shape = CircleShape,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                ) {
                    Icon(
                        Icons.Outlined.Share,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.share))
                }
            }
        }
    }

    if (showShareSheet) {
        CreateShareSheet(
            resourceUri = item.identifier,
            resourceSubtitle = item.metaSubtitle(item.itemCount?.let { itemCountLabel(it) }),
            onDismiss = { showShareSheet = false },
            submit = { uri, mode, receiver -> viewModel.createShareSuspend(uri, mode, receiver) },
            deepLinkFor = viewModel::deepLinkFor,
            bareUrlFor = viewModel::bareUrlFor,
        )
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
private fun DetailsCard(item: ContainerItem, onCopyUrl: () -> Unit) {
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
        if (item.isContainer) {
            item.itemCount?.let {
                DetailRow(stringResource(R.string.details_items), itemCountLabel(it))
            }
        }
        item.createdLabel()?.let { DetailRow(stringResource(R.string.details_created), it) }
        item.modifiedLabel()?.let { DetailRow(stringResource(R.string.details_modified), it) }
        item.parentContainerName()?.let { DetailRow(stringResource(R.string.details_location), it) }
        DetailRow(stringResource(R.string.details_access), accessLabel(item.access))
        UrlRow(url = item.identifier, onCopy = onCopyUrl)
    }
}

@Composable
private fun UrlRow(url: String, onCopy: () -> Unit) {
    val display = url.removePrefix("https://").removePrefix("http://")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.details_url),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(104.dp),
        )
        Spacer(Modifier.width(12.dp))
        Row(
            modifier = Modifier
                .weight(1f)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .clickable(onClick = onCopy)
                .padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = display,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Outlined.ContentCopy,
                contentDescription = stringResource(R.string.copy_link),
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
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

@Preview(name = "Resource preview · file", showBackground = true, widthDp = 360)
@Composable
private fun ResourcePreviewFilePreview() {
    AppTheme {
        Column(Modifier.padding(16.dp)) {
            ResourcePreview(item = PreviewSamples.file())
        }
    }
}

@Preview(name = "Resource preview · folder", showBackground = true, widthDp = 360)
@Composable
private fun ResourcePreviewFolderPreview() {
    AppTheme {
        Column(Modifier.padding(16.dp)) {
            ResourcePreview(item = PreviewSamples.folder())
        }
    }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun DetailsCardPreview() {
    AppTheme {
        Column(Modifier.padding(16.dp)) {
            DetailsCard(item = PreviewSamples.file(), onCopyUrl = {})
        }
    }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun UrlRowPreview() {
    AppTheme {
        Column(Modifier.padding(16.dp)) {
            UrlRow(url = PreviewSamples.RESOURCE, onCopy = {})
        }
    }
}

@Preview(name = "Shared with · loaded", showBackground = true, widthDp = 360)
@Composable
private fun SharedWithCardLoadedPreview() {
    AppTheme {
        Column(Modifier.padding(16.dp)) {
            SharedWithCard(
                state = ResourceDetailsViewModel.SharesState.Loaded(
                    shares = listOf(
                        PreviewSamples.givenShare(name = "ben", mode = ShareMode.READ),
                        PreviewSamples.givenShare(name = "cara", mode = ShareMode.WRITE),
                    ),
                ),
                onManage = {},
            )
        }
    }
}

@Preview(name = "Shared with · loading", showBackground = true, widthDp = 360)
@Composable
private fun SharedWithCardLoadingPreview() {
    AppTheme {
        Column(Modifier.padding(16.dp)) {
            SharedWithCard(state = ResourceDetailsViewModel.SharesState.Loading, onManage = {})
        }
    }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun SectionCardPreview() {
    AppTheme {
        Column(Modifier.padding(16.dp)) {
            SectionCard {
                Text(
                    text = "Details",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                DetailRow(label = "Name", value = "trip.jpg")
                DetailRow(label = "Type", value = "JPEG Image")
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun DetailRowPreview() {
    AppTheme {
        Column(Modifier.padding(16.dp)) {
            DetailRow(label = "Type", value = "JPEG Image")
        }
    }
}
