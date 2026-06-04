package com.erfangholami.solidshare.presentation.container

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.webkit.MimeTypeMap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.erfangholami.solidshare.R
import com.erfangholami.solidshare.domain.model.ContainerItem
import com.erfangholami.solidshare.presentation.isScrollingUp
import com.erfangholami.solidshare.presentation.sharing.ShareLinkPanel
import com.erfangholami.solidshare.presentation.sharing.displayNameForUri
import com.erfangholami.solidshare.util.MIME_TYPE_IMAGE
import com.erfangholami.solidshare.util.MIME_TYPE_VIDEO
import com.erfangholami.solidshare.util.createMediaName
import com.erfangholami.solidshare.util.createMediaUri
import com.erfangholami.solidshare.util.createTakenImageName
import com.erfangholami.solidshare.util.createTakenVideoName
import com.erfangholami.solidshare.util.getPickedFileName
import com.erfangholami.solidshare.util.getVisualMediaType
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Container(
    modifier: Modifier,
    viewModel: ContainerViewModel,
    shareViewModel: com.erfangholami.solidshare.presentation.main.ShareViewModel,
    onContainerClick: (String) -> Unit,
    onResourceInfo: (ContainerItem) -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val screen by viewModel.screenState.collectAsStateWithLifecycle()
    val isRefreshing = screen.isRefreshing
    val isDownloading = screen.isDownloading
    val isCreatingFolder = screen.isCreatingFolder
    val isDeletingResource = screen.isDeletingResource
    val showResourceActionsSheet = screen.showResourceActionsSheet
    val selectedItem = screen.selectedItem
    val containerAccess = screen.containerAccess
    val isFabExpanded = screen.isFabExpanded
    val showMediaSheet = screen.showAddResourceSheet
    val sortField = screen.sortField
    val sortDirection = screen.sortDirection
    val viewMode = screen.viewMode

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val cameraPermissionMessage = stringResource(R.string.camera_permission_needed_capture)
    val openWithChooser = stringResource(R.string.open_with_chooser)
    val noAppMsg = stringResource(R.string.no_app_to_open)

    var showCreateNewFolderDialog by rememberSaveable { mutableStateOf(false) }
    var showDeleteResourceDialog by rememberSaveable { mutableStateOf(false) }
    var shareItemUri by rememberSaveable { mutableStateOf<String?>(null) }
    var reshareLinkItem by rememberSaveable { mutableStateOf<String?>(null) }

    val containerListState = rememberLazyListState()
    val containerGridState = rememberLazyGridState()
    val listIsScrollingUp = containerListState.isScrollingUp()
    val gridIsScrollingUp = containerGridState.isScrollingUp()
    val isFabVisible =
        if (viewMode == ViewMode.LIST) listIsScrollingUp.value else gridIsScrollingUp.value

    val takePhotoLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture(),
    ) { success ->
        viewModel.onCaptureComplete(
            success,
            viewModel.pendingCaptureUri,
            MIME_TYPE_IMAGE,
            createTakenImageName()
        )
    }

    val recordVideoLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CaptureVideo(),
    ) { success ->
        viewModel.onCaptureComplete(
            success,
            viewModel.pendingCaptureUri,
            MIME_TYPE_VIDEO,
            createTakenVideoName()
        )
    }

    var pendingCameraCapture by remember { mutableStateOf<(() -> Unit)?>(null) }
    val cameraPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val capture = pendingCameraCapture
        pendingCameraCapture = null
        if (granted) {
            capture?.invoke()
        } else {
            scope.launch {
                snackbarHostState.showSnackbar(
                    cameraPermissionMessage,
                )
            }
        }
    }
    val withCameraPermission: (() -> Unit) -> Unit = { capture ->
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            capture()
        } else {
            pendingCameraCapture = capture
            cameraPermLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            val mimeType = getVisualMediaType(context, uri)
            val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
            val name = createMediaName(ext)
            viewModel.startUpload(uri, name, mimeType)
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri != null) {
            val mimeType = getVisualMediaType(context, uri)
            val name = getPickedFileName(context, uri)
            viewModel.startUpload(uri, name, mimeType)
        }
    }

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

    Box(modifier = modifier) {
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier.fillMaxSize(),
        ) {
            when (val state = uiState) {
                is ContainerViewModel.UiState.Loading ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }

                is ContainerViewModel.UiState.Error ->
                    ErrorState(
                        message = state.message,
                        onRetry = { viewModel.refresh() },
                        modifier = Modifier.fillMaxSize(),
                    )

                is ContainerViewModel.UiState.Success -> {
                    if (state.items.isEmpty()) {
                        EmptyState(modifier = Modifier.fillMaxSize())
                    } else {
                        Column(modifier = Modifier.fillMaxSize()) {
                            ContainerSortBar(
                                sortField = sortField,
                                sortDirection = sortDirection,
                                viewMode = viewMode,
                                onSortFieldClick = { viewModel.setSortField(it) },
                                onViewModeToggle = { viewModel.toggleViewMode() },
                            )
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            )
                            if (viewMode == ViewMode.LIST) {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    state = containerListState,
                                    contentPadding = PaddingValues(bottom = 88.dp),
                                ) {
                                    items(state.items, key = { it.identifier }) { item ->
                                        ContainerItemRow(
                                            item = item,
                                            onClick = {
                                                viewModel.dismissAddResourceSheet()
                                                if (item.isContainer) {
                                                    onContainerClick(item.identifier)
                                                } else {
                                                    viewModel.onFileClick(item)
                                                }
                                            },
                                            onMoreOptions = { viewModel.onMoreOptionsClick(item) },
                                        )
                                        HorizontalDivider(
                                            modifier = Modifier.padding(start = 80.dp),
                                            color = MaterialTheme.colorScheme.outlineVariant.copy(
                                                alpha = 0.5f
                                            ),
                                        )
                                    }
                                }
                            } else {
                                LazyVerticalGrid(
                                    columns = GridCells.Fixed(2),
                                    modifier = Modifier.fillMaxSize(),
                                    state = containerGridState,
                                    contentPadding = PaddingValues(
                                        start = 12.dp,
                                        end = 12.dp,
                                        top = 12.dp,
                                        bottom = 88.dp,
                                    ),
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    items(state.items, key = { it.identifier }) { item ->
                                        ContainerItemCard(
                                            item = item,
                                            onClick = {
                                                viewModel.dismissAddResourceSheet()
                                                if (item.isContainer) {
                                                    onContainerClick(item.identifier)
                                                } else {
                                                    viewModel.onFileClick(item)
                                                }
                                            },
                                            onMoreOptions = { viewModel.onMoreOptionsClick(item) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

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

        ContainerFab(
            isVisible = isFabVisible && containerAccess.canAddTo,
            isExpanded = isFabExpanded,
            onToggle = { viewModel.toggleFab() },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
        )

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
                onInfo = {
                    val current = selectedItem
                    viewModel.dismissResourceActionsSheet()
                    if (current != null) onResourceInfo(current)
                },
                onShare = {
                    val uri = selectedItem?.identifier
                    viewModel.dismissResourceActionsSheet()
                    if (uri != null) {
                        if (viewModel.isShared) reshareLinkItem = uri else shareItemUri = uri
                    }
                },
                onDownload = { viewModel.onDownloadClick() },
                onOpenWith = { viewModel.onOpenWithClick() },
                onDelete = {
                    viewModel.dismissResourceActionsSheet()
                    showDeleteResourceDialog = true
                },
                showShare = selectedItem?.access?.canShareOnward == true,
                showDelete = containerAccess.canModify,
            )
        }

        showMediaSheet -> {
            AddResourceSheet(
                onDismiss = { viewModel.dismissAddResourceSheet() },
                onUploadFile = {
                    viewModel.dismissAddResourceSheet()
                    filePickerLauncher.launch("*/*")
                },
                onTakePhoto = {
                    viewModel.dismissAddResourceSheet()
                    withCameraPermission {
                        val uri = createMediaUri(context, isVideo = false)
                        viewModel.pendingCaptureUri = uri
                        takePhotoLauncher.launch(uri)
                    }
                },
                onRecordVideo = {
                    viewModel.dismissAddResourceSheet()
                    withCameraPermission {
                        val uri = createMediaUri(context, isVideo = true)
                        viewModel.pendingCaptureUri = uri
                        recordVideoLauncher.launch(uri)
                    }
                },
                onChooseFromGallery = {
                    viewModel.dismissAddResourceSheet()
                    galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                },
                onCreateFolder = {
                    viewModel.dismissAddResourceSheet()
                    showCreateNewFolderDialog = true
                }
            )
        }

        showCreateNewFolderDialog -> {
            CreateNewFolderDialog(
                existingNames = viewModel.getExistingResourceNames(),
                onDismiss = { showCreateNewFolderDialog = false },
                onCreate = { folderName ->
                    showCreateNewFolderDialog = false
                    viewModel.createNewFolder(folderName)
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
