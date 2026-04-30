package com.erfangholami.solidshare.presentation.container

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.webkit.MimeTypeMap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.erfangholami.solidshare.R
import com.erfangholami.solidshare.domain.model.ContainerItem
import com.erfangholami.solidshare.util.MIME_TYPE_IMAGE
import com.erfangholami.solidshare.util.MIME_TYPE_VIDEO
import com.erfangholami.solidshare.util.createMediaName
import com.erfangholami.solidshare.util.createMediaUri
import com.erfangholami.solidshare.util.createTakenImageName
import com.erfangholami.solidshare.util.createTakenVideoName
import com.erfangholami.solidshare.util.getPikedFileName
import com.erfangholami.solidshare.util.getVisualMediaType
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Container(
    modifier: Modifier,
    viewModel: ContainerViewModel,
    onContainerClick: (String) -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val isDownloading by viewModel.isDownloading.collectAsStateWithLifecycle()
    val isCreatingFolder by viewModel.isCreatingFolder.collectAsStateWithLifecycle()
    val isDeletingResource by viewModel.isDeletingResource.collectAsStateWithLifecycle()
    val selectedItem by viewModel.selectedItem.collectAsStateWithLifecycle()
    val isFabExpanded by viewModel.isFabExpanded.collectAsStateWithLifecycle()
    val showMediaSheet by viewModel.showAddResourceSheet.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var showCreateNewFolderDialog by rememberSaveable { mutableStateOf(false) }
    var pendingCameraAction by rememberSaveable { mutableStateOf<CameraAction?>(null) }

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

    val cameraPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            when (pendingCameraAction) {
                CameraAction.PHOTO -> {
                    val uri = createMediaUri(context, isVideo = false)
                    viewModel.pendingCaptureUri = uri
                    takePhotoLauncher.launch(uri)
                }

                CameraAction.VIDEO -> {
                    val uri = createMediaUri(context, isVideo = true)
                    viewModel.pendingCaptureUri = uri
                    recordVideoLauncher.launch(uri)
                }

                else -> {}
            }
        }
        pendingCameraAction = null
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
            val name = getPikedFileName(context, uri)
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
                        context.startActivity(Intent.createChooser(intent, "Open with..."))
                    } catch (_: ActivityNotFoundException) {
                        scope.launch {
                            snackbarHostState.showSnackbar("No app available to open this file type")
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
                        onRetry = {
                            viewModel.refresh()
                        },
                        modifier = Modifier.fillMaxSize(),
                    )

                is ContainerViewModel.UiState.Success -> {
                    if (state.items.isEmpty()) {
                        EmptyState(modifier = Modifier.fillMaxSize())
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
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
                                    onMoreOptions = {
                                        viewModel.onMoreOptionsClick(item)
                                    },
                                )
                                HorizontalDivider(
                                    modifier = Modifier.padding(start = 80.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                )
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
        selectedItem != null -> {
            FileActionsBottomSheet(
                item = selectedItem!!,
                onDismiss = { viewModel.dismissBottomSheet() },
                onShare = {

                },
                onDownload = { viewModel.onDownloadClick() },
                onOpenWith = { viewModel.onOpenWithClick() },
                onDelete = {
                    viewModel.deleteResource(selectedItem!!)
                }
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
                    pendingCameraAction = CameraAction.PHOTO
                    cameraPermLauncher.launch(Manifest.permission.CAMERA)
                },
                onRecordVideo = {
                    viewModel.dismissAddResourceSheet()
                    pendingCameraAction = CameraAction.VIDEO
                    cameraPermLauncher.launch(Manifest.permission.CAMERA)
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
    }
}

@Composable
private fun ContainerItemRow(
    item: ContainerItem,
    onClick: () -> Unit,
    onMoreOptions: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(item.resourceType.tint.copy(alpha = 0.15f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = item.resourceType.icon,
                contentDescription = null,
                modifier = Modifier.size(26.dp),
                tint = item.resourceType.tint,
            )
        }
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = item.getItemSubtitle(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        IconButton(onClick = onMoreOptions) {
            Icon(
                imageVector = Icons.Filled.MoreVert,
                contentDescription = stringResource(R.string.more_options),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.FolderOpen,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
        )
        Spacer(Modifier.height(20.dp))
        Text(
            text = stringResource(R.string.this_folder_is_empty),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.this_folder_is_empty_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Warning,
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            tint = MaterialTheme.colorScheme.error,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = onRetry) {
            Text(
                stringResource(R.string.retry)
            )
        }
    }
}

private enum class CameraAction {
    PHOTO,
    VIDEO,
}