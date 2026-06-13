package com.erfangholami.solidshare.presentation.container

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.erfangholami.solidshare.R
import com.erfangholami.solidshare.domain.model.ContainerItem
import com.erfangholami.solidshare.domain.model.ResourceType
import com.erfangholami.solidshare.presentation.components.ProfileAvatar
import com.erfangholami.solidshare.presentation.components.RowDivider
import com.erfangholami.solidshare.presentation.isScrollingUp
import com.erfangholami.solidshare.presentation.theme.AppTheme
import com.erfangholami.solidshare.util.MIME_TYPE_IMAGE
import com.erfangholami.solidshare.util.MIME_TYPE_VIDEO
import com.erfangholami.solidshare.util.createMediaName
import com.erfangholami.solidshare.util.createMediaUri
import com.erfangholami.solidshare.util.createTakenImageName
import com.erfangholami.solidshare.util.createTakenVideoName
import com.erfangholami.solidshare.util.getPickedFileName
import com.erfangholami.solidshare.util.getVisualMediaType
import kotlinx.coroutines.launch

@Immutable
sealed interface ContainerContent {
    data object Loading : ContainerContent
    data class Error(val message: String) : ContainerContent
    data class Items(val items: List<ContainerItem>) : ContainerContent
}

@Immutable
data class ContainerViewState(
    val content: ContainerContent,
    val title: String? = null,
    val sharerWebId: String? = null,
    val isRefreshing: Boolean = false,
    val sortField: SortField = SortField.DEFAULT,
    val sortDirection: SortDirection = SortDirection.ASCENDING,
    val viewMode: ViewMode = ViewMode.LIST,
    val canAdd: Boolean = false,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContainerView(
    state: ContainerViewState,
    onItemClick: (ContainerItem) -> Unit,
    onItemMoreOptions: (ContainerItem) -> Unit,
    onSortFieldClick: (SortField) -> Unit,
    onToggleViewMode: () -> Unit,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onUpload: (uri: Uri, name: String, mimeType: String) -> Unit,
    onCreateFolder: (name: String) -> Unit,
    existingResourceNames: () -> Set<String>,
    onMessage: (String) -> Unit,
    onBack: (() -> Unit)? = null,
    onSharerClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val gridState = rememberLazyGridState()

    var addSheetOpen by rememberSaveable { mutableStateOf(false) }
    var showCreateFolderDialog by rememberSaveable { mutableStateOf(false) }
    var pendingCaptureUri by rememberSaveable { mutableStateOf<Uri?>(null) }

    val items = (state.content as? ContainerContent.Items)?.items
    val hasItems = !items.isNullOrEmpty()
    val viewMode = state.viewMode

    val listScrollingUp = listState.isScrollingUp()
    val gridScrollingUp = gridState.isScrollingUp()
    val scrollingUp =
        if (viewMode == ViewMode.LIST) listScrollingUp.value else gridScrollingUp.value

    val showScrollToTop by remember(viewMode, listState, gridState) {
        derivedStateOf {
            val index = if (viewMode == ViewMode.LIST) listState.firstVisibleItemIndex
            else gridState.firstVisibleItemIndex
            index > 0
        }
    }

    Box(modifier = modifier) {
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                if (onBack != null || state.title != null || state.sharerWebId != null || hasItems) {
                    ContainerHeader(
                        onBack = onBack,
                        onSharerClick = onSharerClick,
                        title = state.title,
                        sharerWebId = state.sharerWebId,
                        sortField = state.sortField,
                        sortDirection = state.sortDirection,
                        viewMode = viewMode,
                        showControls = hasItems,
                        onSortFieldClick = onSortFieldClick,
                        onToggleViewMode = onToggleViewMode,
                    )
                }
                when (val content = state.content) {
                    ContainerContent.Loading ->
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }

                    is ContainerContent.Error ->
                        ErrorState(
                            message = content.message,
                            onRetry = onRetry,
                            modifier = Modifier.fillMaxSize(),
                        )

                    is ContainerContent.Items ->
                        if (content.items.isEmpty()) {
                            EmptyState(modifier = Modifier.fillMaxSize())
                        } else {
                            RowDivider()
                            Box(modifier = Modifier.fillMaxSize()) {
                                if (viewMode == ViewMode.LIST) {
                                    ContainerItemsList(
                                        items = content.items,
                                        listState = listState,
                                        onItemClick = onItemClick,
                                        onItemMoreOptions = onItemMoreOptions,
                                    )
                                } else {
                                    ContainerItemsGrid(
                                        items = content.items,
                                        gridState = gridState,
                                        onItemClick = onItemClick,
                                        onItemMoreOptions = onItemMoreOptions,
                                    )
                                }
                                ScrollToTopButton(
                                    visible = showScrollToTop,
                                    onClick = {
                                        scope.launch {
                                            if (viewMode == ViewMode.LIST) {
                                                listState.animateScrollToItem(0)
                                            } else {
                                                gridState.animateScrollToItem(0)
                                            }
                                        }
                                    },
                                    modifier = Modifier
                                        .align(Alignment.TopCenter)
                                        .padding(top = 8.dp),
                                )
                            }
                        }
                }
            }
        }

        ContainerFab(
            isVisible = state.canAdd && scrollingUp,
            isExpanded = addSheetOpen,
            onToggle = { addSheetOpen = !addSheetOpen },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
        )

        if (!LocalInspectionMode.current) {
            ContainerAddFlow(
                sheetOpen = addSheetOpen,
                showCreateFolderDialog = showCreateFolderDialog,
                pendingCaptureUri = pendingCaptureUri,
                onPendingCaptureUriChange = { pendingCaptureUri = it },
                onSheetDismiss = { addSheetOpen = false },
                onRequestCreateFolder = { showCreateFolderDialog = true },
                onCreateFolderDismiss = { showCreateFolderDialog = false },
                onCreateFolder = onCreateFolder,
                existingResourceNames = existingResourceNames,
                onUpload = onUpload,
                onMessage = onMessage,
            )
        }
    }
}

@Composable
private fun ContainerHeader(
    onBack: (() -> Unit)?,
    onSharerClick: (() -> Unit)?,
    title: String?,
    sharerWebId: String?,
    sortField: SortField,
    sortDirection: SortDirection,
    viewMode: ViewMode,
    showControls: Boolean,
    onSortFieldClick: (SortField) -> Unit,
    onToggleViewMode: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .padding(start = if (onBack != null) 4.dp else 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.back),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        if (sharerWebId != null) {
            ProfileAvatar(
                webId = sharerWebId,
                displayName = null,
                size = 28.dp,
                modifier = if (onSharerClick != null) {
                    Modifier
                        .clip(CircleShape)
                        .clickable(onClick = onSharerClick)
                } else {
                    Modifier
                },
            )
            Spacer(Modifier.width(10.dp))
        }
        if (title != null) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        } else {
            Spacer(Modifier.weight(1f))
        }
        if (showControls) {
            SortControl(
                sortField = sortField,
                sortDirection = sortDirection,
                onSortFieldClick = onSortFieldClick,
            )
            IconButton(onClick = onToggleViewMode) {
                Icon(
                    imageVector = if (viewMode == ViewMode.LIST) Icons.Filled.GridView
                    else Icons.AutoMirrored.Filled.ViewList,
                    contentDescription = stringResource(
                        if (viewMode == ViewMode.LIST) R.string.grid_view else R.string.list_view
                    ),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SortControl(
    sortField: SortField,
    sortDirection: SortDirection,
    onSortFieldClick: (SortField) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Sort,
                contentDescription = stringResource(R.string.sort_by),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            SortField.entries.forEach { field ->
                val isSelected = field == sortField
                DropdownMenuItem(
                    text = {
                        Text(
                            text = stringResource(field.labelRes()),
                            color = if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface,
                        )
                    },
                    leadingIcon = if (isSelected) {
                        {
                            Icon(
                                imageVector = if (sortDirection == SortDirection.ASCENDING)
                                    Icons.Filled.ArrowUpward else Icons.Filled.ArrowDownward,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    } else null,
                    onClick = {
                        expanded = false
                        onSortFieldClick(field)
                    },
                )
            }
        }
    }
}

@Composable
private fun ScrollToTopButton(
    visible: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn() + slideInVertically { -it },
        exit = fadeOut() + slideOutVertically { -it },
    ) {
        SmallFloatingActionButton(
            onClick = onClick,
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ) {
            Icon(
                imageVector = Icons.Filled.KeyboardArrowUp,
                contentDescription = stringResource(R.string.scroll_to_top),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContainerAddFlow(
    sheetOpen: Boolean,
    showCreateFolderDialog: Boolean,
    pendingCaptureUri: Uri?,
    onPendingCaptureUriChange: (Uri?) -> Unit,
    onSheetDismiss: () -> Unit,
    onRequestCreateFolder: () -> Unit,
    onCreateFolderDismiss: () -> Unit,
    onCreateFolder: (String) -> Unit,
    existingResourceNames: () -> Set<String>,
    onUpload: (Uri, String, String) -> Unit,
    onMessage: (String) -> Unit,
) {
    val context = LocalContext.current
    val cameraPermissionMessage = stringResource(R.string.camera_permission_needed_capture)

    val takePhotoLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture(),
    ) { success ->
        val uri = pendingCaptureUri
        onPendingCaptureUriChange(null)
        if (success && uri != null) onUpload(uri, createTakenImageName(), MIME_TYPE_IMAGE)
    }

    val recordVideoLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CaptureVideo(),
    ) { success ->
        val uri = pendingCaptureUri
        onPendingCaptureUriChange(null)
        if (success && uri != null) onUpload(uri, createTakenVideoName(), MIME_TYPE_VIDEO)
    }

    var pendingCameraCapture by remember { mutableStateOf<(() -> Unit)?>(null) }
    val cameraPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val capture = pendingCameraCapture
        pendingCameraCapture = null
        if (granted) capture?.invoke() else onMessage(cameraPermissionMessage)
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
            onUpload(uri, createMediaName(ext), mimeType)
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri != null) {
            val mimeType = getVisualMediaType(context, uri)
            onUpload(uri, getPickedFileName(context, uri), mimeType)
        }
    }

    if (sheetOpen) {
        AddResourceSheet(
            onDismiss = onSheetDismiss,
            onUploadFile = { filePickerLauncher.launch("*/*") },
            onTakePhoto = {
                withCameraPermission {
                    val uri = createMediaUri(context, isVideo = false)
                    onPendingCaptureUriChange(uri)
                    takePhotoLauncher.launch(uri)
                }
            },
            onRecordVideo = {
                withCameraPermission {
                    val uri = createMediaUri(context, isVideo = true)
                    onPendingCaptureUriChange(uri)
                    recordVideoLauncher.launch(uri)
                }
            },
            onChooseFromGallery = {
                galleryLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo),
                )
            },
            onCreateFolder = onRequestCreateFolder,
        )
    }

    if (showCreateFolderDialog) {
        CreateNewFolderDialog(
            existingNames = existingResourceNames(),
            onDismiss = onCreateFolderDismiss,
            onCreate = { folderName ->
                onCreateFolderDismiss()
                onCreateFolder(folderName)
            },
        )
    }
}

@Composable
private fun ContainerItemsList(
    items: List<ContainerItem>,
    listState: LazyListState,
    onItemClick: (ContainerItem) -> Unit,
    onItemMoreOptions: (ContainerItem) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(bottom = 88.dp),
    ) {
        items(items, key = { it.identifier }) { item ->
            ContainerItemRow(
                item = item,
                onClick = { onItemClick(item) },
                onMoreOptions = { onItemMoreOptions(item) },
            )
            RowDivider(startIndent = 80.dp)
        }
    }
}

@Composable
private fun ContainerItemsGrid(
    items: List<ContainerItem>,
    gridState: LazyGridState,
    onItemClick: (ContainerItem) -> Unit,
    onItemMoreOptions: (ContainerItem) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        state = gridState,
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(items, key = { it.identifier }) { item ->
            ContainerItemCard(
                item = item,
                onClick = { onItemClick(item) },
                onMoreOptions = { onItemMoreOptions(item) },
            )
        }
    }
}

private const val PREVIEW_TIME = 1_717_000_000_000L
private const val PREVIEW_OWNER = "https://alice.solidcommunity.net/profile/card#me"

private fun previewItem(
    name: String,
    isContainer: Boolean,
    type: ResourceType,
    sizeBytes: Long?,
    extension: String? = null,
    mimeType: String? = null,
) = ContainerItem(
    identifier = "https://alice.solidcommunity.net/files/$name",
    isContainer = isContainer,
    name = name,
    extension = extension,
    mimeType = mimeType,
    resourceType = type,
    resourceTypes = emptyList(),
    sizeBytes = sizeBytes,
    lastModified = PREVIEW_TIME,
    etag = null,
)

private fun previewItems(): List<ContainerItem> = listOf(
    previewItem("Documents", true, ResourceType.FOLDER, null),
    previewItem("Photos", true, ResourceType.FOLDER, null),
    previewItem("budget.xlsx", false, ResourceType.SPREADSHEET, 84_213, "xlsx", "application/vnd.ms-excel"),
    previewItem("trip.jpg", false, ResourceType.IMAGE, 2_415_919, "jpg", "image/jpeg"),
    previewItem("notes.md", false, ResourceType.DOCUMENT, 1_240, "md", "text/markdown"),
    previewItem("contract.pdf", false, ResourceType.PDF, 538_214, "pdf", "application/pdf"),
)

private fun previewState(
    content: ContainerContent = ContainerContent.Items(previewItems()),
    title: String? = null,
    sharerWebId: String? = null,
    viewMode: ViewMode = ViewMode.LIST,
    canAdd: Boolean = true,
) = ContainerViewState(
    content = content,
    title = title,
    sharerWebId = sharerWebId,
    viewMode = viewMode,
    canAdd = canAdd,
)

@Composable
private fun ContainerViewPreviewHost(isDark: Boolean = false, content: @Composable () -> Unit) {
    AppTheme(isDarkTheme = isDark) {
        Surface(color = MaterialTheme.colorScheme.background) {
            content()
        }
    }
}

@Composable
private fun PreviewContainerView(state: ContainerViewState, onBack: (() -> Unit)? = null) {
    ContainerView(
        state = state,
        onItemClick = {},
        onItemMoreOptions = {},
        onSortFieldClick = {},
        onToggleViewMode = {},
        onRefresh = {},
        onRetry = {},
        onUpload = { _, _, _ -> },
        onCreateFolder = {},
        existingResourceNames = { emptySet() },
        onMessage = {},
        onBack = onBack,
        modifier = Modifier.fillMaxSize(),
    )
}

@Preview(name = "Owner · pod root", showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun ContainerViewRootPreview() {
    ContainerViewPreviewHost {
        PreviewContainerView(previewState())
    }
}

@Preview(name = "Owner · folder", showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun ContainerViewFolderPreview() {
    ContainerViewPreviewHost {
        PreviewContainerView(previewState(title = "Photos"), onBack = {})
    }
}

@Preview(name = "Shared · read-only", showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun ContainerViewSharedReadOnlyPreview() {
    ContainerViewPreviewHost {
        PreviewContainerView(
            previewState(title = "Trip 2024", sharerWebId = PREVIEW_OWNER, canAdd = false),
            onBack = {},
        )
    }
}

@Preview(name = "Shared · can edit", showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun ContainerViewSharedEditPreview() {
    ContainerViewPreviewHost {
        PreviewContainerView(
            previewState(title = "Team Files", sharerWebId = PREVIEW_OWNER, canAdd = true),
            onBack = {},
        )
    }
}

@Preview(name = "Grid view", showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun ContainerViewGridPreview() {
    ContainerViewPreviewHost {
        PreviewContainerView(previewState(title = "Photos", viewMode = ViewMode.GRID))
    }
}

@Preview(name = "Empty", showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun ContainerViewEmptyPreview() {
    ContainerViewPreviewHost {
        PreviewContainerView(
            previewState(content = ContainerContent.Items(emptyList()), title = "New Folder"),
        )
    }
}

@Preview(name = "Loading", showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun ContainerViewLoadingPreview() {
    ContainerViewPreviewHost {
        PreviewContainerView(previewState(content = ContainerContent.Loading, title = "Photos"))
    }
}

@Preview(name = "Error", showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun ContainerViewErrorPreview() {
    ContainerViewPreviewHost {
        PreviewContainerView(
            previewState(
                content = ContainerContent.Error("Couldn't load this folder. Check your connection."),
                title = "Photos",
            ),
        )
    }
}

@Preview(name = "Shared · dark", showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun ContainerViewSharedDarkPreview() {
    ContainerViewPreviewHost(isDark = true) {
        PreviewContainerView(
            previewState(title = "Team Files", sharerWebId = PREVIEW_OWNER, canAdd = true),
            onBack = {},
        )
    }
}
