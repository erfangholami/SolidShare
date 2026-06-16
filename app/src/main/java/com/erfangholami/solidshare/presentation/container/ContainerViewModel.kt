package com.erfangholami.solidshare.presentation.container

import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.erfangholami.solidshare.R
import com.erfangholami.solidshare.data.repo.auth.AuthRepository
import com.erfangholami.solidshare.data.repo.file.FileRepository
import com.erfangholami.solidshare.data.repo.sharing.SharingRepository
import com.erfangholami.solidshare.domain.model.ContainerItem
import com.erfangholami.solidshare.domain.model.ResourceAccess
import com.erfangholami.solidshare.presentation.sharing.displayNameForUri
import com.erfangholami.solidshare.util.MIME_TYPE_OCTET_STREAM
import com.erfangholami.solidshare.util.StringProvider
import com.erfangholami.solidshare.worker.DownloadWorker
import com.erfangholami.solidshare.worker.UploadWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class ContainerViewModel @Inject constructor(
    private val stringProvider: StringProvider,
    private val savedStateHandle: SavedStateHandle,
    private val workManager: WorkManager,
    private val authRepository: AuthRepository,
    private val fileRepository: FileRepository,
    private val sharingRepository: SharingRepository,
) : ViewModel() {

    sealed class UiState {
        object Loading : UiState()

        @Immutable
        data class Success(val items: List<ContainerItem>) : UiState()
        data class Error(val message: String) : UiState()
    }

    sealed class FileOpenEvent {
        data class OpenFile(val file: File, val mimeType: String) : FileOpenEvent()
        data class Error(val message: String) : FileOpenEvent()
    }

    data class ScreenState(
        val isRefreshing: Boolean = false,
        val isDownloading: Boolean = false,
        val isCreatingFolder: Boolean = false,
        val isDeletingResource: Boolean = false,
        val isDuplicating: Boolean = false,
        val showResourceActionsSheet: Boolean = false,
        val selectedItem: ContainerItem? = null,
        val containerAccess: ResourceAccess = ResourceAccess.FULL,
        val sortField: SortField = SortField.DEFAULT,
        val sortDirection: SortDirection = SortDirection.ASCENDING,
        val viewMode: ViewMode = ViewMode.LIST,
    )

    private val containerUrl: String? = savedStateHandle.get<String>("containerUrl")

    private val shared: Boolean = savedStateHandle.get<Boolean>("shared") ?: false

    val isShared: Boolean get() = shared

    val sharedOwnerWebId: String? = savedStateHandle.get<String>("ownerWebId")

    val title: String? = containerUrl?.let { displayNameForUri(it) }

    val sharerWebId: String? = sharedOwnerWebId

    @Volatile
    private var resolvedContainerUrl: String? = null

    private val _activeWebId = MutableStateFlow<String?>(null)

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _screenState = MutableStateFlow(
        ScreenState(containerAccess = if (shared) ResourceAccess.READ_ONLY else ResourceAccess.FULL)
    )
    val screenState: StateFlow<ScreenState> = _screenState.asStateFlow()

    private val _fileOpenEvent = MutableSharedFlow<FileOpenEvent>()
    val fileOpenEvent: SharedFlow<FileOpenEvent> = _fileOpenEvent.asSharedFlow()

    private val _folderCreationError = MutableSharedFlow<String>()
    val folderCreationError: SharedFlow<String> = _folderCreationError.asSharedFlow()

    private val _resourceDeletionError = MutableSharedFlow<String>()
    val resourceDeletion: SharedFlow<String> = _resourceDeletionError.asSharedFlow()

    private val _duplicateMessage = MutableSharedFlow<String>()
    val duplicateMessage: SharedFlow<String> = _duplicateMessage.asSharedFlow()

    private var rawItems: List<ContainerItem> = emptyList()

    private var loadJob: Job? = null

    private var statsJob: Job? = null

    init {
        viewModelScope.launch {
            authRepository.activeWebIdFlow
                .filterNotNull()
                .distinctUntilChanged()
                .collect { webId ->
                    val previous = _activeWebId.value
                    _activeWebId.value = webId
                    when {
                        containerUrl == null -> load(reset = previous != null && previous != webId)
                        previous == null -> load()
                    }
                }
        }
    }

    fun refresh() {
        load()
    }

    fun onFileClick(item: ContainerItem) {
        if (item.isContainer || _screenState.value.isDownloading) return
        viewModelScope.launch {
            _screenState.update { it.copy(isDownloading = true) }
            try {
                val webId = _activeWebId.value ?: run {
                    _fileOpenEvent.emit(FileOpenEvent.Error(stringProvider.getString(R.string.error_no_active_user)))
                    return@launch
                }
                val downloaded = fileRepository.downloadFile(webId, item.identifier)
                _fileOpenEvent.emit(
                    FileOpenEvent.OpenFile(File(downloaded.path), downloaded.mimeType)
                )
            } catch (e: Exception) {
                _fileOpenEvent.emit(FileOpenEvent.Error(e.message ?: stringProvider.getString(R.string.error_open_file)))
            } finally {
                _screenState.update { it.copy(isDownloading = false) }
            }
        }
    }

    fun onMoreOptionsClick(item: ContainerItem) {
        _screenState.update { it.copy(selectedItem = item, showResourceActionsSheet = true) }
    }

    fun dismissResourceActionsSheet() {
        _screenState.update { it.copy(showResourceActionsSheet = false) }
    }

    fun onDownloadClick() {
        val item = _screenState.value.selectedItem ?: return
        dismissResourceActionsSheet()
        viewModelScope.launch {
            val webId = _activeWebId.value ?: return@launch
            val fileName = item.name
            val mimeType = item.mimeType ?: MIME_TYPE_OCTET_STREAM
            val request = OneTimeWorkRequestBuilder<DownloadWorker>()
                .setInputData(
                    workDataOf(
                        DownloadWorker.KEY_WEB_ID to webId,
                        DownloadWorker.KEY_FILE_URL to item.identifier,
                        DownloadWorker.KEY_FILE_NAME to fileName,
                        DownloadWorker.KEY_MIME_TYPE to mimeType,
                    ),
                )
                .build()
            workManager.enqueue(request)
        }
    }

    fun onOpenWithClick() {
        val item = _screenState.value.selectedItem ?: return
        dismissResourceActionsSheet()
        onFileClick(item)
    }

    fun startUpload(fileUri: Uri, fileName: String, mimeType: String) {
        viewModelScope.launch {
            if (!_screenState.value.containerAccess.canAddTo) {
                _folderCreationError.emit(stringProvider.getString(R.string.error_no_permission_for_action))
                return@launch
            }
            val webId = _activeWebId.value ?: return@launch
            val containerUrl = resolvedContainerUrl ?: return@launch
            val request = OneTimeWorkRequestBuilder<UploadWorker>()
                .setInputData(
                    workDataOf(
                        UploadWorker.KEY_WEB_ID to webId,
                        UploadWorker.KEY_CONTAINER_URL to containerUrl,
                        UploadWorker.KEY_FILE_URI to fileUri.toString(),
                        UploadWorker.KEY_FILE_NAME to fileName,
                        UploadWorker.KEY_MIME_TYPE to mimeType,
                    ),
                )
                .build()
            workManager.enqueue(request)

            workManager.getWorkInfoByIdFlow(request.id).collect { info ->
                if (info?.state == WorkInfo.State.SUCCEEDED) {
                    load()
                }
            }
        }
    }

    private fun load(reset: Boolean = false) {
        loadJob?.cancel()
        statsJob?.cancel()

        if (reset) {
            resolvedContainerUrl = null
            rawItems = emptyList()
            _screenState.update { it.copy(isRefreshing = false) }
            _uiState.value = UiState.Loading
        } else {
            val alreadyHasContent = _uiState.value is UiState.Success
            if (alreadyHasContent) {
                _screenState.update { it.copy(isRefreshing = true) }
            } else {
                _uiState.value = UiState.Loading
            }
        }

        loadJob = viewModelScope.launch {
            try {
                val webId = _activeWebId.value ?: run {
                    _uiState.value = UiState.Error(stringProvider.getString(R.string.error_no_active_user))
                    return@launch
                }
                val url = containerUrl ?: run {
                    authRepository.getStorages(webId).firstOrNull() ?: run {
                        _uiState.value = UiState.Error(stringProvider.getString(R.string.error_no_storage))
                        return@launch
                    }
                }
                resolvedContainerUrl = url
                if (shared) {
                    val access = runCatching { fileRepository.probeAccess(webId, url) }
                        .getOrDefault(ResourceAccess.READ_ONLY)
                    _screenState.update { it.copy(containerAccess = access) }
                }
                val items =
                    fileRepository.getContainerContents(webId, url, includeItemAccess = shared)
                rawItems = items
                applySort()
                computeFolderItemCounts(webId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = UiState.Error(e.message ?: stringProvider.getString(R.string.error_unknown))
            } finally {
                if (isActive) _screenState.update { it.copy(isRefreshing = false) }
            }
        }
    }

    private fun computeFolderItemCounts(webId: String) {
        val containers = rawItems.filter { it.isContainer }
        if (containers.isEmpty()) return
        statsJob = viewModelScope.launch {
            containers.forEach { container ->
                launch {
                    val count = runCatching {
                        fileRepository.getContainerItemCount(webId, container.identifier)
                    }.getOrNull() ?: return@launch
                    rawItems = rawItems.map { item ->
                        if (item.identifier == container.identifier) {
                            item.copy(itemCount = count)
                        } else {
                            item
                        }
                    }
                    applySort()
                }
            }
        }
    }

    fun createNewFolder(folderName: String) {
        viewModelScope.launch {
            if (!_screenState.value.containerAccess.canAddTo) {
                _folderCreationError.emit(stringProvider.getString(R.string.error_no_permission_for_action))
                return@launch
            }
            _screenState.update { it.copy(isCreatingFolder = true) }
            try {
                val webId = _activeWebId.value ?: return@launch
                val containerUrl = resolvedContainerUrl ?: return@launch
                fileRepository.createFolder(webId, containerUrl, folderName)
                load()
            } catch (e: Exception) {
                _folderCreationError.emit(e.message ?: stringProvider.getString(R.string.error_create_folder))
            } finally {
                _screenState.update { it.copy(isCreatingFolder = false) }
            }
        }
    }

    fun getExistingResourceNames(): Set<String> {
        return if (_uiState.value is UiState.Success) {
            (_uiState.value as UiState.Success).items.map { item ->
                runCatching {
                    Uri.decode(item.name)
                }.getOrElse { item.name }
            }.toSet()
        } else {
            emptySet()
        }
    }

    fun setSortField(field: SortField) {
        _screenState.update { current ->
            if (current.sortField == field) {
                current.copy(
                    sortDirection = if (current.sortDirection == SortDirection.ASCENDING)
                        SortDirection.DESCENDING else SortDirection.ASCENDING,
                )
            } else {
                current.copy(sortField = field, sortDirection = SortDirection.ASCENDING)
            }
        }
        applySort()
    }

    fun toggleViewMode() {
        _screenState.update {
            it.copy(viewMode = if (it.viewMode == ViewMode.LIST) ViewMode.GRID else ViewMode.LIST)
        }
    }

    private fun applySort() {
        _uiState.value = UiState.Success(sortedItems(rawItems))
    }

    private fun sortedItems(items: List<ContainerItem>): List<ContainerItem> {
        val state = _screenState.value
        val fieldComparator: Comparator<ContainerItem> = when (state.sortField) {
            SortField.DEFAULT,
            SortField.NAME -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.name }
            SortField.LAST_MODIFIED -> compareBy(nullsLast(naturalOrder())) { it.lastModified }
            SortField.SIZE -> compareBy(nullsLast(naturalOrder())) { it.sizeBytes }
        }
        val directed = if (state.sortDirection == SortDirection.DESCENDING)
            fieldComparator.reversed() else fieldComparator
        return if (state.sortField == SortField.DEFAULT) {
            items.sortedWith(compareByDescending<ContainerItem> { it.isContainer }.then(directed))
        } else {
            items.sortedWith(directed)
        }
    }

    fun deleteResource() {
        viewModelScope.launch {
            val selectedItem = _screenState.value.selectedItem ?: return@launch
            if (!_screenState.value.containerAccess.canModify || !selectedItem.access.canWrite) {
                dismissResourceActionsSheet()
                _resourceDeletionError.emit(stringProvider.getString(R.string.error_no_permission_for_action))
                return@launch
            }
            _screenState.update { it.copy(isDeletingResource = true) }
            dismissResourceActionsSheet()
            try {
                val webId = _activeWebId.value ?: return@launch
                fileRepository.deleteResource(
                    webId,
                    selectedItem.identifier,
                    selectedItem.isContainer,
                )
                load()
            } catch (e: Exception) {
                _resourceDeletionError.emit(e.message ?: stringProvider.getString(R.string.error_delete_resource))
            } finally {
                _screenState.update { it.copy(isDeletingResource = false) }
            }
        }
    }

    fun duplicateResource() {
        val item = _screenState.value.selectedItem ?: return
        dismissResourceActionsSheet()
        viewModelScope.launch {
            if (!_screenState.value.containerAccess.canModify) {
                _duplicateMessage.emit(stringProvider.getString(R.string.error_no_permission_for_action))
                return@launch
            }
            _screenState.update { it.copy(isDuplicating = true) }
            try {
                val webId = _activeWebId.value ?: return@launch
                val created = fileRepository.duplicateResource(webId, item)
                created.forEach { uri ->
                    runCatching { sharingRepository.makePrivate(webId, uri) }
                }
                load()
                _duplicateMessage.emit(stringProvider.getString(R.string.resource_duplicated))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _duplicateMessage.emit(e.message ?: stringProvider.getString(R.string.error_duplicate))
            } finally {
                _screenState.update { it.copy(isDuplicating = false) }
            }
        }
    }
}
