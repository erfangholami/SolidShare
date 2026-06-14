package com.erfangholami.solidshare.presentation.sharing

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.erfangholami.solidshare.data.repo.auth.AuthRepository
import com.erfangholami.solidshare.domain.model.PublicProfile
import com.erfangholami.solidshare.presentation.navigation.ChooseReceiverRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChooseReceiverViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<ChooseReceiverRoute>()
    val resourceUri: String = route.resourceUri
    val ownerWebId: String? = route.ownerWebId

    val accounts: StateFlow<List<PublicProfile>> = authRepository.loggedInProfilesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val activeWebId: StateFlow<String?> = authRepository.activeWebIdFlow

    fun selectReceiver(webId: String, onSwitched: () -> Unit) {
        viewModelScope.launch {
            authRepository.setActiveWebId(webId)
            onSwitched()
        }
    }
}
