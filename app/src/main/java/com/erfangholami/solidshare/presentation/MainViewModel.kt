package com.erfangholami.solidshare.presentation

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.erfangholami.solidshare.presentation.navigation.NavGraphRegistry
import com.erfangholami.solidshare.presentation.navigation.NavGraphContributor
import com.erfangholami.solidshare.presentation.sharing.ReceiverPickerRegistry
import com.erfangholami.solidshare.presentation.sharing.ScanRouter
import com.erfangholami.solidshare.presentation.sharing.ReceiverPickerContributor
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
    receiverPickers: ReceiverPickerRegistry,
    navGraphs: NavGraphRegistry,
    private val sharingRepository: SharingRepository,
    private val scanRouter: ScanRouter,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    val receiverPicker: ReceiverPickerContributor? = receiverPickers.preferred()

    val moduleGraphs: Set<NavGraphContributor> = navGraphs.all()

    val themeMode: StateFlow<ThemeMode> = settingsRepository.getSettingPreferences()
        .map { it.themeMode }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThemeMode.SYSTEM)

    private val _pendingShareLink = MutableStateFlow<ParsedShareLink?>(null)
    val pendingShareLink: StateFlow<ParsedShareLink?> = _pendingShareLink.asStateFlow()

    private val _pendingModuleRoute = MutableStateFlow<Any?>(null)
    val pendingModuleRoute: StateFlow<Any?> = _pendingModuleRoute.asStateFlow()

    fun handleDeepLink(intent: Intent?) {
        val raw = intent?.data?.toString() ?: return
        parseRawLink(raw)
    }

    private fun parseRawLink(raw: String) {
        sharingRepository.parseDeepLink(raw)?.let {
            _pendingShareLink.value = it
            return
        }
        scanRouter.route(raw)?.let {
            _pendingModuleRoute.value = it
        }
    }

    fun handleIncomingFile(bytes: ByteArray, fileName: String? = null) {
        scanRouter.routeContent(bytes, fileName)?.let {
            _pendingModuleRoute.value = it
        }
    }

    fun consumePendingShareLink() {
        _pendingShareLink.value = null
    }

    fun consumePendingModuleRoute() {
        _pendingModuleRoute.value = null
    }
}
