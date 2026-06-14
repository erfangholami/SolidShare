package com.erfangholami.solidshare.presentation

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.erfangholami.solidshare.domain.model.ThemeMode
import com.erfangholami.solidshare.presentation.navigation.AppNavHost
import com.erfangholami.solidshare.presentation.theme.AppTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    private var openNotifications by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        viewModel.handleDeepLink(intent)
        if (intent?.getBooleanExtra(EXTRA_OPEN_NOTIFICATIONS, false) == true) {
            openNotifications = true
        }
        setContent {
            val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
            val pendingShareLink by viewModel.pendingShareLink.collectAsStateWithLifecycle()
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
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        viewModel.handleDeepLink(intent)
        if (intent.getBooleanExtra(EXTRA_OPEN_NOTIFICATIONS, false)) {
            openNotifications = true
        }
    }

    companion object {
        const val EXTRA_OPEN_NOTIFICATIONS = "open_notifications"
    }
}
