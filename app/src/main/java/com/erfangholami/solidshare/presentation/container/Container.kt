package com.erfangholami.solidshare.presentation.container

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.erfangholami.solidshare.R
import com.erfangholami.solidshare.domain.model.ContainerItem
import com.erfangholami.solidshare.presentation.sharing.ShareLinkPanel
import com.erfangholami.solidshare.presentation.sharing.displayNameForUri
import com.erfangholami.solidshare.presentation.util.copyText
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Container(
    modifier: Modifier,
    viewModel: ContainerViewModel,
    shareViewModel: com.erfangholami.solidshare.presentation.main.ShareViewModel,
    onContainerClick: (String) -> Unit,
    onResourceInfo: (ContainerItem) -> Unit,
    onManageAccess: (ContainerItem) -> Unit,
    onBack: (() -> Unit)? = null,
    onSharerClick: (() -> Unit)? = null,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val screen by viewModel.screenState.collectAsStateWithLifecycle()
    val isDownloading = screen.isDownloading
    val isCreatingFolder = screen.isCreatingFolder
    val isDeletingResource = screen.isDeletingResource
    val isDuplicating = screen.isDuplicating
    val showResourceActionsSheet = screen.showResourceActionsSheet
    val selectedItem = screen.selectedItem
    val containerAccess = screen.containerAccess

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val openWithChooser = stringResource(R.string.open_with_chooser)
    val noAppMsg = stringResource(R.string.no_app_to_open)
    val clipboard = LocalClipboard.current
    val linkCopiedMsg = stringResource(R.string.link_copied)
    val openInUnavailableMsg = stringResource(R.string.open_in_unavailable)

    var showDeleteResourceDialog by rememberSaveable { mutableStateOf(false) }
    var shareItemUri by rememberSaveable { mutableStateOf<String?>(null) }
    var reshareLinkItem by rememberSaveable { mutableStateOf<String?>(null) }

    val storagePermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    val notifPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            storagePermLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        viewModel.fileOpenEvent.collect { event ->
            when (event) {
                is ContainerViewModel.FileOpenEvent.OpenFile -> {
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
                        scope.launch {
                            snackbarHostState.showSnackbar(noAppMsg)
                        }
                    }
                }

                is ContainerViewModel.FileOpenEvent.Error ->
                    scope.launch { snackbarHostState.showSnackbar(event.message) }
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.folderCreationError.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.resourceDeletion.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.duplicateMessage.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    val content = when (val state = uiState) {
        is ContainerViewModel.UiState.Loading -> ContainerContent.Loading
        is ContainerViewModel.UiState.Error -> ContainerContent.Error(state.message)
        is ContainerViewModel.UiState.Success -> ContainerContent.Items(state.items)
    }

    Box(modifier = modifier) {
        ContainerView(
            state = ContainerViewState(
                content = content,
                title = viewModel.title,
                sharerWebId = viewModel.sharerWebId,
                isRefreshing = screen.isRefreshing,
                sortField = screen.sortField,
                sortDirection = screen.sortDirection,
                viewMode = screen.viewMode,
                canAdd = containerAccess.canAddTo,
            ),
            onItemClick = { item ->
                if (item.isContainer) {
                    onContainerClick(item.identifier)
                } else {
                    viewModel.onFileClick(item)
                }
            },
            onItemMoreOptions = { viewModel.onMoreOptionsClick(it) },
            onSortFieldClick = { viewModel.setSortField(it) },
            onToggleViewMode = { viewModel.toggleViewMode() },
            onRefresh = { viewModel.refresh() },
            onRetry = { viewModel.refresh() },
            onUpload = { uri, name, mime -> viewModel.startUpload(uri, name, mime) },
            onCreateFolder = { viewModel.createNewFolder(it) },
            existingResourceNames = { viewModel.getExistingResourceNames() },
            onMessage = { message -> scope.launch { snackbarHostState.showSnackbar(message) } },
            onBack = onBack,
            onSharerClick = onSharerClick,
            modifier = Modifier.fillMaxSize(),
        )

        if (isDownloading) {
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
                        stringResource(R.string.opening),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
        }

        if (isCreatingFolder) {
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
                        stringResource(R.string.creating_folder),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
        }

        if (isDeletingResource) {
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
                        stringResource(R.string.deleting_resource),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
        }

        if (isDuplicating) {
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
                        stringResource(R.string.duplicating),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 88.dp, start = 16.dp, end = 16.dp),
        )
    }

    when {
        showResourceActionsSheet -> {
            FileActionsBottomSheet(
                item = selectedItem!!,
                onDismiss = { viewModel.dismissResourceActionsSheet() },
                onShare = {
                    val uri = selectedItem.identifier
                    viewModel.dismissResourceActionsSheet()
                    if (viewModel.isShared) reshareLinkItem = uri else shareItemUri = uri
                },
                onManageAccess = {
                    val current = selectedItem
                    viewModel.dismissResourceActionsSheet()
                    onManageAccess(current)
                },
                onDuplicate = { viewModel.duplicateResource() },
                onDownload = { viewModel.onDownloadClick() },
                onCopyLink = {
                    val uri = selectedItem.identifier
                    viewModel.dismissResourceActionsSheet()
                    scope.launch {
                        clipboard.copyText(uri)
                        snackbarHostState.showSnackbar(linkCopiedMsg)
                    }
                },
                onOpenIn = {
                    viewModel.dismissResourceActionsSheet()
                    scope.launch { snackbarHostState.showSnackbar(openInUnavailableMsg) }
                },
                onInfo = {
                    val current = selectedItem
                    viewModel.dismissResourceActionsSheet()
                    onResourceInfo(current)
                },
                onDelete = {
                    viewModel.dismissResourceActionsSheet()
                    showDeleteResourceDialog = true
                },
                showShare = selectedItem.access.canShareOnward,
                showManage = !viewModel.isShared && containerAccess.canModify,
                showDuplicate = !viewModel.isShared && containerAccess.canModify,
                showDelete = containerAccess.canModify,
            )
        }

        showDeleteResourceDialog -> {
            DeleteResourceDialog(
                resourceName = selectedItem?.name.orEmpty(),
                onDismiss = { showDeleteResourceDialog = false },
                onDelete = {
                    showDeleteResourceDialog = false
                    viewModel.deleteResource()
                },
            )
        }
    }

    shareItemUri?.let { uri ->
        com.erfangholami.solidshare.presentation.sharing.CreateShareSheet(
            initialResourceUri = uri,
            onDismiss = { shareItemUri = null },
            submit = { resourceUri, mode, receiver ->
                shareViewModel.createShareSuspend(resourceUri, mode, receiver)
            },
            deepLinkFor = shareViewModel::deepLinkFor,
        )
    }

    reshareLinkItem?.let { uri ->
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { reshareLinkItem = null },
            sheetState = sheetState,
        ) {
            ShareLinkPanel(
                title = displayNameForUri(uri),
                deepLink = shareViewModel.reshareLinkFor(uri, viewModel.sharedOwnerWebId),
                bareUrl = shareViewModel.bareUrlFor(uri),
                onClose = { reshareLinkItem = null },
            )
        }
    }
}
