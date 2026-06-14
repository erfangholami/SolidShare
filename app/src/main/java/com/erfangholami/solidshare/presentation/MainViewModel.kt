package com.erfangholami.solidshare.presentation

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.erfangholami.solidshare.data.repo.settings.SettingsRepository
import com.erfangholami.solidshare.data.repo.sharing.SharingRepository
import com.erfangholami.solidshare.domain.model.ParsedShareLink
import com.erfangholami.solidshare.domain.model.ThemeMode
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
    settingsRepository: SettingsRepository,
) : ViewModel() {

    val themeMode: StateFlow<ThemeMode> = settingsRepository.getSettingPreferences()
        .map { it.themeMode }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThemeMode.SYSTEM)

    private val _pendingShareLink = MutableStateFlow<ParsedShareLink?>(null)
    val pendingShareLink: StateFlow<ParsedShareLink?> = _pendingShareLink.asStateFlow()

    fun handleDeepLink(intent: Intent?) {
        val data = intent?.data ?: return
        val parsed = sharingRepository.parseDeepLink(data.toString()) ?: return
        _pendingShareLink.value = parsed
    }

    fun consumePendingShareLink() {
        _pendingShareLink.value = null
    }
}
