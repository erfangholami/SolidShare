package com.erfangholami.solidshare.presentation.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.erfangholami.solidshare.R
import com.erfangholami.solidshare.data.repo.auth.AuthRepository
import com.erfangholami.solidshare.data.repo.profile.PublicProfileRepository
import com.erfangholami.solidshare.domain.model.ProfileEdits
import com.erfangholami.solidshare.domain.model.PublicProfile
import com.erfangholami.solidshare.util.StringProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class EditProfileViewModel @Inject constructor(
    private val stringProvider: StringProvider,
    private val authRepository: AuthRepository,
    private val publicProfileRepository: PublicProfileRepository,
) : ViewModel() {

    sealed class SaveState {
        data object Idle : SaveState()
        data object Saving : SaveState()
        data object Success : SaveState()
        data class Error(val message: String) : SaveState()
    }

    val profile: StateFlow<PublicProfile?> = authRepository.activeProfileFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _saveState = MutableStateFlow<SaveState>(SaveState.Idle)
    val saveState: StateFlow<SaveState> = _saveState.asStateFlow()

    fun save(edits: ProfileEdits) {
        val webId = profile.value?.webId ?: return
        if (_saveState.value is SaveState.Saving) return
        _saveState.value = SaveState.Saving
        viewModelScope.launch {
            val result = publicProfileRepository.updateProfile(webId, edits)
            _saveState.value = result.fold(
                onSuccess = {
                    runCatching { authRepository.reloadProfile(webId) }
                    SaveState.Success
                },
                onFailure = { SaveState.Error(it.message ?: stringProvider.getString(R.string.error_unknown)) },
            )
        }
    }

    fun consumeSaveState() {
        _saveState.value = SaveState.Idle
    }
}
