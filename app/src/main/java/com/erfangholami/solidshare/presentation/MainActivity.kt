package com.erfangholami.solidshare.presentation

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.IntentCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.erfangholami.solidshare.domain.model.ThemeMode
import com.erfangholami.solidshare.presentation.navigation.AppNavHost
import com.erfangholami.solidshare.presentation.theme.AppTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    private var openNotifications by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        viewModel.handleDeepLink(intent)
        handleIncomingPass(intent)
        if (intent?.getBooleanExtra(EXTRA_OPEN_NOTIFICATIONS, false) == true) {
            openNotifications = true
        }
        setContent {
            val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
            val pendingShareLink by viewModel.pendingShareLink.collectAsStateWithLifecycle()
            val pendingTicketDraft by viewModel.pendingTicketDraft.collectAsStateWithLifecycle()
            val isDarkTheme = when (themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }
            AppTheme(isDarkTheme = isDarkTheme) {
                AppNavHost(
                    openNotifications = openNotifications,
                    onOpenNotificationsHandled = { openNotifications = false },
                    pendingShareLink = pendingShareLink,
                    onShareLinkHandled = { viewModel.consumePendingShareLink() },
                    pendingTicketDraft = pendingTicketDraft,
                    onTicketDraftHandled = { viewModel.consumePendingTicketDraft() },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        viewModel.handleDeepLink(intent)
        handleIncomingPass(intent)
        if (intent.getBooleanExtra(EXTRA_OPEN_NOTIFICATIONS, false)) {
            openNotifications = true
        }
    }

    private fun handleIncomingPass(intent: Intent?) {
        intent ?: return
        val uri = when (intent.action) {
            Intent.ACTION_VIEW -> intent.data
                ?.takeIf { it.scheme == "content" || it.scheme == "file" }

            Intent.ACTION_SEND ->
                IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)

            else -> null
        } ?: return
        val looksLikePass = intent.type == PKPASS_MIME_TYPE ||
                uri.toString().endsWith(".pkpass", ignoreCase = true)
        if (!looksLikePass) return
        lifecycleScope.launch(Dispatchers.IO) {
            val bytes = runCatching {
                contentResolver.openInputStream(uri)?.use { it.readBytes() }
            }.getOrNull() ?: return@launch
            viewModel.handlePassFile(bytes)
        }
    }

    companion object {
        const val EXTRA_OPEN_NOTIFICATIONS = "open_notifications"
        private const val PKPASS_MIME_TYPE = "application/vnd.apple.pkpass"
    }
}
