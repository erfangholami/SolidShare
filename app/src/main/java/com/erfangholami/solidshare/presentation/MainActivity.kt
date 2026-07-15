package com.erfangholami.solidshare.presentation

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
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
    private var openContacts by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        viewModel.handleDeepLink(intent)
        handleIncomingTicket(intent)
        if (intent?.getBooleanExtra(EXTRA_OPEN_NOTIFICATIONS, false) == true) {
            openNotifications = true
        }
        if (intent?.getBooleanExtra(EXTRA_OPEN_CONTACTS, false) == true) {
            openContacts = true
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
                    openContacts = openContacts,
                    onOpenContactsHandled = { openContacts = false },
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
        handleIncomingTicket(intent)
        if (intent.getBooleanExtra(EXTRA_OPEN_NOTIFICATIONS, false)) {
            openNotifications = true
        }
        if (intent.getBooleanExtra(EXTRA_OPEN_CONTACTS, false)) {
            openContacts = true
        }
    }

    private fun handleIncomingTicket(intent: Intent?) {
        intent ?: return
        // A shared link (e.g. a ticket or "Add to Google Wallet" URL) arrives as SEND text.
        if (intent.action == Intent.ACTION_SEND && intent.type?.startsWith("text/") == true) {
            intent.getStringExtra(Intent.EXTRA_TEXT)?.let { viewModel.handleSharedText(it) }
            return
        }
        val uri = when (intent.action) {
            Intent.ACTION_VIEW -> intent.data
                ?.takeIf { it.scheme == "content" || it.scheme == "file" }

            Intent.ACTION_SEND ->
                IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)

            else -> null
        } ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            if (fileSize(uri).let { it != null && it > MAX_TICKET_FILE_BYTES }) return@launch
            val bytes = runCatching {
                contentResolver.openInputStream(uri)?.use { it.readBytes() }
            }.getOrNull() ?: return@launch
            if (bytes.size > MAX_TICKET_FILE_BYTES) return@launch
            viewModel.handleTicketFile(bytes, fileName = displayName(uri))
        }
    }

    private fun displayName(uri: Uri): String? {
        if (uri.scheme == "content") {
            runCatching {
                contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                    ?.use { cursor ->
                        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (cursor.moveToFirst() && index >= 0) return cursor.getString(index)
                    }
            }
        }
        return uri.lastPathSegment
    }

    private fun fileSize(uri: Uri): Long? = runCatching {
        contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (cursor.moveToFirst() && index >= 0 && !cursor.isNull(index)) cursor.getLong(index) else null
        }
    }.getOrNull()

    companion object {
        const val EXTRA_OPEN_NOTIFICATIONS = "open_notifications"
        const val EXTRA_OPEN_CONTACTS = "open_contacts"
        private const val MAX_TICKET_FILE_BYTES = 20L * 1024 * 1024
    }
}
