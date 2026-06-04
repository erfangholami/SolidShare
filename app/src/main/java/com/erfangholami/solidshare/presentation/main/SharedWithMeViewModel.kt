package com.erfangholami.solidshare.presentation.main

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.erfangholami.solidshare.data.repo.auth.AuthRepository
import com.erfangholami.solidshare.data.repo.notifications.NotificationsRepository
import com.erfangholami.solidshare.data.repo.sharing.SharingRepository
import com.erfangholami.solidshare.domain.model.ReceivedShare
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SharedWithMeViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val sharingRepository: SharingRepository,
    private val notificationsRepository: NotificationsRepository,
) : ViewModel() {

    enum class GroupBy { SENDER, TIME }

    @Immutable
    data class UiState(
        val shares: List<ReceivedShare> = emptyList(),
        val groupBy: GroupBy = GroupBy.SENDER,
        val isLoading: Boolean = false,
        val isRefreshing: Boolean = false,
        val verifyResult: VerifyResult? = null,
        val error: String? = null,
    )

    sealed class VerifyResult {
        data class Granted(val resourceUri: String, val mode: String) : VerifyResult()
        data class NotGranted(val resourceUri: String) : VerifyResult()
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun setGroupBy(groupBy: GroupBy) {
        _uiState.value = _uiState.value.copy(groupBy = groupBy)
    }

    fun load() {
        viewModelScope.launch {
            val webId = authRepository.getActiveWebId() ?: return@launch
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val received = sharingRepository.getStoredReceivedShares(webId)
                _uiState.value = _uiState.value.copy(
                    shares = received,
                    isLoading = false,
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            val webId = authRepository.getActiveWebId() ?: return@launch
            _uiState.value = _uiState.value.copy(isRefreshing = true, error = null)
            try {
                runCatching { notificationsRepository.listNotifications(webId) }
                val received = sharingRepository.refreshReceivedShares(webId)
                _uiState.value = _uiState.value.copy(
                    shares = received,
                    isRefreshing = false,
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isRefreshing = false, error = e.message)
            }
        }
    }

    fun verifyAndAdd(rawUrl: String) {
        viewModelScope.launch {
            val webId = authRepository.getActiveWebId() ?: return@launch
            val parsed = sharingRepository.parseDeepLink(rawUrl)
            val resourceUri = parsed?.resourceUri ?: rawUrl
            _uiState.value = _uiState.value.copy(isRefreshing = true, error = null)
            try {
                val received =
                    sharingRepository.addReceivedShare(webId, resourceUri, parsed?.ownerWebId)
                if (received != null) {
                    val list = sharingRepository.getStoredReceivedShares(webId)
                    _uiState.value = _uiState.value.copy(
                        shares = list,
                        isRefreshing = false,
                        verifyResult = VerifyResult.Granted(
                            resourceUri = received.resourceUri,
                            mode = received.mode.name.lowercase(),
                        ),
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isRefreshing = false,
                        verifyResult = VerifyResult.NotGranted(resourceUri),
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isRefreshing = false,
                    error = e.message,
                )
            }
        }
    }

    fun removeShare(share: ReceivedShare) {
        viewModelScope.launch {
            val webId = authRepository.getActiveWebId() ?: return@launch
            try {
                sharingRepository.removeReceivedShare(
                    webId, share.resourceUri, share.ownerWebId,
                )
                load()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    fun clearVerifyResult() {
        _uiState.value = _uiState.value.copy(verifyResult = null)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
