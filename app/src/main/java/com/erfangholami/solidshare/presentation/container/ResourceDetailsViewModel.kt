package com.erfangholami.solidshare.presentation.container

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.erfangholami.solidshare.R
import com.erfangholami.solidshare.data.repo.auth.AuthRepository
import com.erfangholami.solidshare.data.repo.file.FileRepository
import com.erfangholami.solidshare.data.repo.sharing.SharingRepository
import com.erfangholami.solidshare.domain.model.ContainerItem
import com.erfangholami.solidshare.domain.model.GivenShare
import com.erfangholami.solidshare.domain.model.ShareMode
import com.erfangholami.solidshare.domain.model.ShareReceiver
import com.erfangholami.solidshare.presentation.navigation.ResourceDetailsRoute
import com.erfangholami.solidshare.presentation.navigation.resourceDetailsTypeMap
import com.erfangholami.solidshare.util.StringProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ResourceDetailsViewModel @Inject constructor(
    private val stringProvider: StringProvider,
    savedStateHandle: SavedStateHandle,
    private val authRepository: AuthRepository,
    private val sharingRepository: SharingRepository,
    private val fileRepository: FileRepository,
) : ViewModel() {

    sealed interface SharesState {
        data object Loading : SharesState

        @Immutable
        data class Loaded(val shares: List<GivenShare>) : SharesState
        data class Error(val message: String) : SharesState
    }

    private val routeItem: ContainerItem = savedStateHandle
        .toRoute<ResourceDetailsRoute>(resourceDetailsTypeMap)
        .item

    private val _itemState = MutableStateFlow(routeItem)
    val itemState: StateFlow<ContainerItem> = _itemState.asStateFlow()

    val canManageSharing: Boolean = routeItem.access.canControl
    val canShare: Boolean = routeItem.access.canShareOnward
    val resourceUri: String = routeItem.identifier

    private var ownerWebId: String? = null

    private val _sharesState = MutableStateFlow<SharesState>(SharesState.Loading)
    val sharesState: StateFlow<SharesState> = _sharesState.asStateFlow()

    init {
        loadCreatedTime()
        loadItemCount()
    }

    private fun loadCreatedTime() {
        viewModelScope.launch {
            val webId = authRepository.getActiveWebId() ?: return@launch
            ownerWebId = webId
            val created = runCatching {
                fileRepository.getResourceCreatedTime(webId, routeItem)
            }.getOrNull() ?: return@launch
            _itemState.value = _itemState.value.copy(createdTime = created)
        }
    }

    private fun loadItemCount() {
        if (!routeItem.isContainer) return
        viewModelScope.launch {
            val webId = authRepository.getActiveWebId() ?: return@launch
            val count = runCatching {
                fileRepository.getContainerItemCount(webId, routeItem.identifier)
            }.getOrNull() ?: return@launch
            _itemState.value = _itemState.value.copy(itemCount = count)
        }
    }

    fun loadShares() {
        if (!canManageSharing) {
            _sharesState.value = SharesState.Loaded(emptyList())
            return
        }
        viewModelScope.launch {
            _sharesState.value = SharesState.Loading
            try {
                val webId = authRepository.getActiveWebId() ?: error("Not signed in")
                _sharesState.value =
                    SharesState.Loaded(
                        sharingRepository.getGivenSharesForResource(
                            webId,
                            routeItem.identifier
                        )
                    )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _sharesState.value = SharesState.Error(e.message ?: stringProvider.getString(R.string.manage_load_failed))
            }
        }
    }

    suspend fun createShareSuspend(
        resourceUri: String,
        mode: ShareMode,
        receiver: ShareReceiver,
    ): GivenShare {
        val webId = authRepository.getActiveWebId() ?: error("Not signed in")
        ownerWebId = webId
        val share = sharingRepository.createShare(webId, resourceUri, mode, receiver)
        loadShares()
        return share
    }

    fun deepLinkFor(resourceUri: String): String =
        sharingRepository.deepLinkFor(resourceUri, ownerWebId)

    fun bareUrlFor(resourceUri: String): String =
        sharingRepository.bareUrlFor(resourceUri)
}
