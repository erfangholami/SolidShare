package com.erfangholami.solidshare.presentation.sharing

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.erfangholami.solidshare.R
import com.erfangholami.solidshare.data.repo.profile.PublicProfileRepository
import com.erfangholami.solidshare.domain.model.PublicProfile
import com.erfangholami.solidshare.presentation.navigation.PublicProfileRoute
import com.erfangholami.solidshare.util.StringProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PublicProfileViewModel @Inject constructor(
    private val stringProvider: StringProvider,
    savedStateHandle: SavedStateHandle,
    private val publicProfileRepository: PublicProfileRepository,
) : ViewModel() {

    sealed class UiState {
        data object Loading : UiState()
        data class Success(val profile: PublicProfile) : UiState()
        data class Error(val message: String) : UiState()
    }

    val webId: String = savedStateHandle.toRoute<PublicProfileRoute>().webId

    private val _state = MutableStateFlow<UiState>(UiState.Loading)
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.value = UiState.Loading
            val result = publicProfileRepository.fetchByWebId(webId)
            _state.value = result.fold(
                onSuccess = { UiState.Success(it) },
                onFailure = { UiState.Error(it.message ?: stringProvider.getString(R.string.error_unknown)) },
            )
        }
    }
}
