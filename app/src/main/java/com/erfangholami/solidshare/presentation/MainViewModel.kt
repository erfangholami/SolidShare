package com.erfangholami.solidshare.presentation

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.erfangholami.solidshare.data.repo.auth.AuthRepository
import com.erfangholami.solidshare.data.repo.settings.SettingsRepository
import com.erfangholami.solidshare.data.repo.sharing.SharingRepository
import com.erfangholami.solidshare.domain.model.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val sharingRepository: SharingRepository,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    val themeMode: StateFlow<ThemeMode> = settingsRepository.getSettingPreferences()
        .map { it.themeMode }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThemeMode.SYSTEM)

    fun handleDeepLink(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme != "solidshare") return
        val parsed = sharingRepository.parseDeepLink(data.toString()) ?: return
        viewModelScope.launch {
            val webId = authRepository.getActiveWebId() ?: return@launch
            runCatching {
                sharingRepository.addReceivedShare(webId, parsed.resourceUri, parsed.ownerWebId)
            }
        }
    }
}
