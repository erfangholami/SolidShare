package com.erfangholami.solidshare.presentation.container

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import com.erfangholami.solidshare.presentation.components.BlockingProgressOverlay
import com.erfangholami.solidshare.presentation.components.ResourceAction
import com.erfangholami.solidshare.presentation.components.ResourceActions
import com.erfangholami.solidshare.presentation.components.ResourceActionsSheet
import com.erfangholami.solidshare.presentation.sharing.ShareLinkPanel
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
    val makeOfflineUnavailableMsg = stringResource(R.string.make_offline_unavailable)

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
            BlockingProgressOverlay(label = stringResource(R.string.opening))
        }
        if (isCreatingFolder) {
            BlockingProgressOverlay(label = stringResource(R.string.creating_folder))
        }
        if (isDeletingResource) {
            BlockingProgressOverlay(label = stringResource(R.string.deleting_resource))
        }
        if (isDuplicating) {
            BlockingProgressOverlay(label = stringResource(R.string.duplicating))
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
            val actionItem = selectedItem!!
            ResourceActionsSheet(
                resourceUri = actionItem.identifier,
                subtitle = actionItem.metaSubtitle(
                    actionItem.itemCount?.let { itemCountLabel(it) },
                ),
                actions = if (viewModel.isShared) {
                    ResourceActions.sharedFolderChild(
                        isContainer = actionItem.isContainer,
                        canEdit = containerAccess.canWrite && actionItem.access.canWrite,
                    )
                } else {
                    ResourceActions.ownerPod(isContainer = actionItem.isContainer)
                },
                onDismiss = { viewModel.dismissResourceActionsSheet() },
                onAction = { action ->
                    when (action) {
                        ResourceAction.SHARE -> shareItemUri = actionItem.identifier
                        ResourceAction.MANAGE_ACCESS -> onManageAccess(actionItem)
                        ResourceAction.DUPLICATE -> viewModel.duplicateResource()
                        ResourceAction.DOWNLOAD -> viewModel.onDownloadClick()
                        ResourceAction.MAKE_OFFLINE ->
                            scope.launch { snackbarHostState.showSnackbar(makeOfflineUnavailableMsg) }

                        ResourceAction.COPY_LINK -> scope.launch {
                            clipboard.copyText(actionItem.identifier)
                            snackbarHostState.showSnackbar(linkCopiedMsg)
                        }

                        ResourceAction.OPEN_IN ->
                            scope.launch { snackbarHostState.showSnackbar(openInUnavailableMsg) }

                        ResourceAction.INFO -> onResourceInfo(actionItem)
                        ResourceAction.DELETE -> showDeleteResourceDialog = true
                        else -> Unit
                    }
                },
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
        val shareSubtitle = selectedItem?.takeIf { it.identifier == uri }
            ?.let { item -> item.metaSubtitle(item.itemCount?.let { c -> itemCountLabel(c) }) }
        com.erfangholami.solidshare.presentation.sharing.CreateShareSheet(
            resourceUri = uri,
            resourceSubtitle = shareSubtitle,
            onDismiss = { shareItemUri = null },
            submit = { resourceUri, mode, receiver ->
                shareViewModel.createShareSuspend(resourceUri, mode, receiver)
            },
            deepLinkFor = shareViewModel::deepLinkFor,
            bareUrlFor = shareViewModel::bareUrlFor,
        )
    }

    reshareLinkItem?.let { uri ->
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { reshareLinkItem = null },
            sheetState = sheetState,
        ) {
            ShareLinkPanel(
                resourceUri = uri,
                deepLink = shareViewModel.reshareLinkFor(uri, viewModel.sharedOwnerWebId),
                bareUrl = shareViewModel.bareUrlFor(uri),
            )
        }
    }
}
