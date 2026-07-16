package com.erfangholami.solidshare.presentation

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.erfangholami.solidshare.data.passimport.TicketLinkFetcher
import com.erfangholami.solidshare.data.repo.settings.SettingsRepository
import com.erfangholami.solidshare.data.repo.sharing.SharingRepository
import com.erfangholami.solidshare.data.repo.tickets.TicketsRepository
import com.erfangholami.solidshare.domain.model.ParsedShareLink
import com.erfangholami.solidshare.domain.model.ThemeMode
import com.erfangholami.solidshare.domain.model.TicketDraft
import com.erfangholami.solidshare.presentation.wallet.TicketImportHolder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val sharingRepository: SharingRepository,
    private val ticketsRepository: TicketsRepository,
    private val ticketImportHolder: TicketImportHolder,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    val themeMode: StateFlow<ThemeMode> = settingsRepository.getSettingPreferences()
        .map { it.themeMode }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThemeMode.SYSTEM)

    private val _pendingShareLink = MutableStateFlow<ParsedShareLink?>(null)
    val pendingShareLink: StateFlow<ParsedShareLink?> = _pendingShareLink.asStateFlow()

    private val _pendingTicketDraft = MutableStateFlow<TicketDraft?>(null)
    val pendingTicketDraft: StateFlow<TicketDraft?> = _pendingTicketDraft.asStateFlow()

    private val _pendingImport = MutableStateFlow(false)
    val pendingImport: StateFlow<Boolean> = _pendingImport.asStateFlow()

    fun handleDeepLink(intent: Intent?) {
        val raw = intent?.data?.toString() ?: return
        parseRawLink(raw)
    }

    fun handleSharedText(text: String) {
        parseRawLink(text.trim())
    }

    private fun parseRawLink(raw: String) {
        sharingRepository.parseDeepLink(raw)?.let {
            _pendingShareLink.value = it
            return
        }
        ticketsRepository.parseTicketQr(raw)?.let {
            _pendingTicketDraft.value = it
            return
        }
        TicketLinkFetcher.firstHttpsUrl(raw)?.let {
            ticketImportHolder.stashLink(it)
            _pendingImport.value = true
        }
    }

    fun handleTicketFile(bytes: ByteArray, fileName: String? = null) {
        ticketImportHolder.stashImport(bytes, fileName)
        _pendingImport.value = true
    }

    fun consumePendingShareLink() {
        _pendingShareLink.value = null
    }

    fun consumePendingTicketDraft() {
        _pendingTicketDraft.value = null
    }

    fun consumePendingImport() {
        _pendingImport.value = false
    }
}
