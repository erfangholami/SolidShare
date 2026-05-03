package com.erfangholami.solidshare.presentation.container

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.erfangholami.solidshare.data.repo.auth.AuthRepository
import com.erfangholami.solidshare.data.repo.file.FileRepository
import com.erfangholami.solidshare.domain.model.ContainerItem
import com.erfangholami.solidshare.presentation.base.BaseViewModel
import com.erfangholami.solidshare.worker.DownloadWorker
import com.erfangholami.solidshare.worker.UploadWorker
import com.pondersource.shared.domain.network.HTTPAcceptType.OCTET_STREAM
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class ContainerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @param:ApplicationContext private val appContext: Context,
    private val authRepository: AuthRepository,
    private val fileRepository: FileRepository,
) : BaseViewModel() {

    sealed class UiState {
        object Loading : UiState()
        data class Success(val items: List<ContainerItem>) : UiState()
        data class Error(val message: String) : UiState()
    }

    sealed class FileOpenEvent {
        data class OpenFile(val file: File, val mimeType: String) : FileOpenEvent()
        data class Error(val message: String) : FileOpenEvent()
    }

    private val containerUrl: String? = savedStateHandle.get<String>("containerUrl")

    @Volatile
    private var resolvedContainerUrl: String? = null

    @Volatile
    var pendingCaptureUri: Uri? = null

    private val _activeWebId = MutableStateFlow<String?>(null)

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _isDownloading = MutableStateFlow(false)
    val isDownloading: StateFlow<Boolean> = _isDownloading.asStateFlow()

    private val _fileOpenEvent = MutableSharedFlow<FileOpenEvent>()
    val fileOpenEvent: SharedFlow<FileOpenEvent> = _fileOpenEvent.asSharedFlow()

    private val _selectedItem = MutableStateFlow<ContainerItem?>(null)
    val selectedItem: StateFlow<ContainerItem?> = _selectedItem.asStateFlow()

    private val _showResourceActionsSheet = MutableStateFlow(false)
    val showResourceActionsSheet: StateFlow<Boolean> = _showResourceActionsSheet.asStateFlow()

    private val _isFabExpanded = MutableStateFlow(false)
    val isFabExpanded: StateFlow<Boolean> = _isFabExpanded.asStateFlow()

    private val _showAddResourceSheet = MutableStateFlow(false)
    val showAddResourceSheet: StateFlow<Boolean> = _showAddResourceSheet.asStateFlow()

    private val _isCreatingFolder = MutableStateFlow(false)
    val isCreatingFolder: StateFlow<Boolean> = _isCreatingFolder.asStateFlow()

    private val _folderCreationError = MutableSharedFlow<String>()
    val folderCreationError: SharedFlow<String> = _folderCreationError.asSharedFlow()

    private val _isDeletingResource = MutableStateFlow(false)
    val isDeletingResource: StateFlow<Boolean> = _isDeletingResource.asStateFlow()

    private val _resourceDeletionError = MutableSharedFlow<String>()
    val resourceDeletion: SharedFlow<String> = _resourceDeletionError.asSharedFlow()

    private var rawItems: List<ContainerItem> = emptyList()

    private val _sortField = MutableStateFlow(SortField.NAME)
    val sortField: StateFlow<SortField> = _sortField.asStateFlow()

    private val _sortDirection = MutableStateFlow(SortDirection.ASCENDING)
    val sortDirection: StateFlow<SortDirection> = _sortDirection.asStateFlow()

    private val _viewMode = MutableStateFlow(ViewMode.LIST)
    val viewMode: StateFlow<ViewMode> = _viewMode.asStateFlow()

    init {
        viewModelScope.launch {
            authRepository.activeWebIdFlow
                .filterNotNull()
                .distinctUntilChanged()
                .collect {
                    _activeWebId.value = it
                }
        }

        viewModelScope.launch {
            if (containerUrl == null) {
                authRepository.activeWebIdFlow
                    .filterNotNull()
                    .distinctUntilChanged()
                    .collect { load() }
            } else {
                load()
            }
        }
    }

    fun refresh() {
        load()
    }

    fun onFileClick(item: ContainerItem) {
        if (item.isContainer || _isDownloading.value) return
        viewModelScope.launch(Dispatchers.IO) {
            _isDownloading.value = true
            try {
                val webId = _activeWebId.value ?: run {
                    _fileOpenEvent.emit(FileOpenEvent.Error("No active user"))
                    return@launch
                }
                val downloaded = fileRepository.downloadFile(webId, item.identifier)
                _fileOpenEvent.emit(FileOpenEvent.OpenFile(downloaded.file, downloaded.mimeType))
            } catch (e: Exception) {
                _fileOpenEvent.emit(FileOpenEvent.Error(e.message ?: "Failed to open file"))
            } finally {
                _isDownloading.value = false
            }
        }
    }

    fun onMoreOptionsClick(item: ContainerItem) {
        dismissAddResourceSheet()
        _selectedItem.value = item
        _showResourceActionsSheet.value = true
    }

    fun dismissResourceActionsSheet() {
        _showResourceActionsSheet.value = false
    }

    fun onDownloadClick() {
        val item = _selectedItem.value ?: return
        dismissResourceActionsSheet()
        viewModelScope.launch(Dispatchers.IO) {
            val webId = _activeWebId.value ?: return@launch
            val fileName = item.name
            val mimeType = item.mimeType ?: OCTET_STREAM
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
            WorkManager.getInstance(appContext).enqueue(request)
        }
    }

    fun onOpenWithClick() {
        val item = _selectedItem.value ?: return
        dismissResourceActionsSheet()
        onFileClick(item)
    }

    fun toggleFab() {
        _isFabExpanded.value = !_isFabExpanded.value
        _showAddResourceSheet.value = !_showAddResourceSheet.value
    }

    fun dismissAddResourceSheet() {
        _isFabExpanded.value = false
        _showAddResourceSheet.value = false
    }

    fun startUpload(fileUri: Uri, fileName: String, mimeType: String) {
        dismissAddResourceSheet()
        viewModelScope.launch(Dispatchers.IO) {
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
            val workManager = WorkManager.getInstance(appContext)
            workManager.enqueue(request)

            workManager.getWorkInfoByIdFlow(request.id).collect { info ->
                if (info?.state == WorkInfo.State.SUCCEEDED) {
                    load()
                }
            }
        }
    }

    fun onCaptureComplete(success: Boolean, uri: Uri?, mimeType: String, fileName: String) {
        if (!success || uri == null) {
            pendingCaptureUri = null
            return
        }
        startUpload(uri, fileName, mimeType)
        pendingCaptureUri = null
    }

    private fun load() {
        if (_isRefreshing.value) return

        val alreadyHasContent = _uiState.value is UiState.Success
        if (alreadyHasContent) {
            _isRefreshing.value = true
        } else {
            _uiState.value = UiState.Loading
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val webId = _activeWebId.value ?: run {
                    _uiState.value = UiState.Error("No active user")
                    return@launch
                }
                val url = containerUrl ?: run {
                    val profile = authRepository.getProfile(webId)
                    profile.webId?.getStorages()?.firstOrNull()?.toString() ?: run {
                        _uiState.value = UiState.Error("No storage found")
                        return@launch
                    }
                }
                resolvedContainerUrl = url
                val items = fileRepository.getContainerContents(webId, url)
                rawItems = items
                applySort()
            } catch (e: Exception) {
                _uiState.value = UiState.Error(e.message ?: "Unknown error")
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun createNewFolder(folderName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _isCreatingFolder.value = true
            try {
                val webId = _activeWebId.value ?: return@launch
                val containerUrl = resolvedContainerUrl ?: return@launch
                fileRepository.createFolder(webId, containerUrl, folderName)
                load()
            } catch (e: Exception) {
                _folderCreationError.emit(e.message ?: "Failed to create folder")
            } finally {
                _isCreatingFolder.value = false
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
        if (_sortField.value == field) {
            _sortDirection.value = if (_sortDirection.value == SortDirection.ASCENDING)
                SortDirection.DESCENDING else SortDirection.ASCENDING
        } else {
            _sortField.value = field
            _sortDirection.value = SortDirection.ASCENDING
        }
        applySort()
    }

    fun toggleViewMode() {
        _viewMode.value = if (_viewMode.value == ViewMode.LIST) ViewMode.GRID else ViewMode.LIST
    }

    private fun applySort() {
        _uiState.value = UiState.Success(sortedItems(rawItems))
    }

    private fun sortedItems(items: List<ContainerItem>): List<ContainerItem> {
        val fieldComparator: Comparator<ContainerItem> = when (_sortField.value) {
            SortField.NAME -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.name }
            SortField.LAST_MODIFIED -> compareBy(nullsLast(naturalOrder())) { it.lastModified }
            SortField.SIZE -> compareBy(nullsLast(naturalOrder())) { it.sizeBytes }
        }
        val directed = if (_sortDirection.value == SortDirection.DESCENDING)
            fieldComparator.reversed() else fieldComparator
        return items.sortedWith(compareByDescending<ContainerItem> { it.isContainer }.then(directed))
    }

    fun deleteResource() {
        viewModelScope.launch(Dispatchers.IO) {
            val selectedItem = _selectedItem.value ?: return@launch
            _isDeletingResource.value = true
            dismissResourceActionsSheet()
            try {
                val webId = _activeWebId.value ?: return@launch
                fileRepository.deleteResource(
                    webId,
                    selectedItem.identifier,
                    selectedItem.isContainer
                )
                load()
            } catch (e: Exception) {
                _resourceDeletionError.emit(e.message ?: "Failed to create folder")
            } finally {
                _isDeletingResource.value = false
            }
        }
    }
}
