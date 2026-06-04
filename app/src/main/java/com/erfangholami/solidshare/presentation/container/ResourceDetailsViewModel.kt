package com.erfangholami.solidshare.presentation.container

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.erfangholami.solidshare.R
import com.erfangholami.solidshare.data.repo.auth.AuthRepository
import com.erfangholami.solidshare.data.repo.sharing.SharingRepository
import com.erfangholami.solidshare.domain.model.ContainerItem
import com.erfangholami.solidshare.domain.model.GivenShare
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
) : ViewModel() {

    sealed interface SharesState {
        data object Loading : SharesState

        @Immutable
        data class Loaded(val shares: List<GivenShare>) : SharesState
        data class Error(val message: String) : SharesState
    }

    val item: ContainerItem = savedStateHandle
        .toRoute<ResourceDetailsRoute>(resourceDetailsTypeMap)
        .item

    val canManageSharing: Boolean = item.access.canControl

    private val _sharesState = MutableStateFlow<SharesState>(SharesState.Loading)
    val sharesState: StateFlow<SharesState> = _sharesState.asStateFlow()

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
                            item.identifier
                        )
                    )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _sharesState.value = SharesState.Error(e.message ?: stringProvider.getString(R.string.manage_load_failed))
            }
        }
    }
}
