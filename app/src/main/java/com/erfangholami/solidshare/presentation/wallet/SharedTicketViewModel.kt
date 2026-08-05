package com.erfangholami.solidshare.presentation.wallet

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.erfangholami.solidshare.R
import com.erfangholami.solidshare.data.passimport.PassImages
import com.erfangholami.solidshare.data.repo.auth.AuthRepository
import com.erfangholami.solidshare.data.repo.tickets.TicketsRepository
import com.erfangholami.solidshare.domain.model.Ticket
import com.erfangholami.solidshare.presentation.navigation.SharedTicketRoute
import com.erfangholami.solidshare.util.NetworkMonitor
import com.erfangholami.solidshare.domain.error.AppError
import com.erfangholami.solidshare.domain.error.AppOperation
import com.erfangholami.solidshare.domain.error.ErrorPresenter
import com.erfangholami.solidshare.domain.error.UiError
import com.erfangholami.solidshare.domain.error.asException
import com.erfangholami.solidshare.domain.error.rethrowIfCancellation
import com.erfangholami.solidshare.util.StringProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class SharedTicketViewModel @Inject constructor(
    private val stringProvider: StringProvider,
    private val errors: ErrorPresenter,
    savedStateHandle: SavedStateHandle,
    private val authRepository: AuthRepository,
    private val ticketsRepository: TicketsRepository,
    private val networkMonitor: NetworkMonitor,
) : ViewModel() {

    sealed interface UiState {
        data object Loading : UiState
        data object Offline : UiState

        @Immutable
        data class Loaded(
            val ticket: Ticket,
            val visuals: PassImages?,
            val inWallet: Boolean,
            val adding: Boolean,
        ) : UiState

        data class Error(val error: UiError) : UiState
    }

    private val route = savedStateHandle.toRoute<SharedTicketRoute>()
    val ownerWebId: String? = route.ownerWebId

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _messages = MutableSharedFlow<String>()
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    init {
        load()
    }

    fun load() {
        if (!networkMonitor.currentlyOnline()) {
            _uiState.value = UiState.Offline
            return
        }
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            try {
                val webId = authRepository.getActiveWebId()
                    ?: throw AppError.NoActiveAccount.asException()
                val ticket = ticketsRepository.getSharedTicket(webId, route.resourceUri)
                val visuals = runCatching {
                    ticketsRepository.getSharedTicketImages(webId, ticket)
                }.getOrNull()
                val inWallet = runCatching {
                    ticketsRepository.findTicketCopiedFrom(webId, ticket.uri)
                }.getOrNull() != null
                _uiState.value = UiState.Loaded(
                    ticket = ticket,
                    visuals = visuals,
                    inWallet = inWallet,
                    adding = false,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = UiState.Error(
                    errors.present(e, AppOperation.OPEN_SHARED_ITEM, origin = route.resourceUri),
                )
            }
        }
    }

    fun addToWallet() {
        val loaded = _uiState.value as? UiState.Loaded ?: return
        if (loaded.inWallet || loaded.adding) return
        viewModelScope.launch {
            _uiState.value = loaded.copy(adding = true)
            try {
                val webId = authRepository.getActiveWebId()
                    ?: throw AppError.NoActiveAccount.asException()
                ticketsRepository.addSharedTicketToWallet(webId, loaded.ticket)
                _uiState.value = loaded.copy(inWallet = true, adding = false)
                _messages.emit(stringProvider.getString(R.string.added_to_wallet))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = loaded.copy(adding = false)
                _messages.emit(
                    errors.message(e, AppOperation.COPY_SHARED_ITEM, origin = route.resourceUri),
                )
            }
        }
    }
}
