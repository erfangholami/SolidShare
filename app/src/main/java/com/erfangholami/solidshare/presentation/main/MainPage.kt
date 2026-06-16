package com.erfangholami.solidshare.presentation.main

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.erfangholami.solidshare.R
import com.erfangholami.solidshare.presentation.navigation.MainNavItem
import com.erfangholami.solidshare.presentation.navigation.ScanRoute
import com.erfangholami.solidshare.presentation.permissions.rememberPermissionGate

private const val RECEIVED_SHARE_MSG_KEY = "received_share_msg"

@Composable
fun MainPage(
    parentNavController: NavController,
) {

    val nestedNavController = rememberNavController()
    val shareViewModel = hiltViewModel<ShareViewModel>()
    val snackbarHostState = remember { SnackbarHostState() }

    val notificationPromptViewModel = hiltViewModel<NotificationPromptViewModel>()
    val shouldPromptNotifications by notificationPromptViewModel
        .shouldPromptAfterLogin.collectAsStateWithLifecycle()
    val notificationsGate = rememberPermissionGate(
        permission = Manifest.permission.POST_NOTIFICATIONS,
        required = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU,
        rationaleTitle = stringResource(R.string.notifications_permission_title),
        rationaleText = stringResource(R.string.notifications_permission_rationale),
        settingsText = stringResource(R.string.notifications_permission_settings_rationale),
        onComplete = { notificationPromptViewModel.markPrompted() },
    )

    LaunchedEffect(shouldPromptNotifications) {
        if (shouldPromptNotifications) {
            if (notificationsGate.isGranted) {
                notificationPromptViewModel.markPrompted()
            } else {
                notificationsGate.run {}
            }
        }
    }

    val nestedEntry by nestedNavController.currentBackStackEntryAsState()
    val parentEntry by parentNavController.currentBackStackEntryAsState()

    val onHomeTab = nestedEntry?.destination?.hasRoute(MainNavItem.Home::class) == true

    val onBackClicked: () -> Unit = {
        if (nestedNavController.graph.findStartDestination().id == nestedNavController.currentDestination?.id) {
            parentNavController.popBackStack()
        } else {
            nestedNavController.popBackStack()
        }
    }

    val bottomItems = remember {
        listOf(
            MainNavItem.MainNavBottomItem.HomeItem,
            MainNavItem.MainNavBottomItem.DirectoryItem,
            MainNavItem.MainNavBottomItem.ShareItem,
            MainNavItem.MainNavBottomItem.ProfileItem,
        )
    }

    val changeTab: (t: Any) -> Unit = { tabRoute ->
        nestedNavController.navigate(tabRoute) {
            popUpTo(nestedNavController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    val openProfileTab: () -> Unit = { changeTab(MainNavItem.Profile) }

    LaunchedEffect(parentEntry) {
        val handle = parentEntry?.savedStateHandle ?: return@LaunchedEffect
        handle.getStateFlow<String?>(RECEIVED_SHARE_MSG_KEY, null).collect { msg ->
            if (msg != null) {
                snackbarHostState.showSnackbar(msg)
                handle[RECEIVED_SHARE_MSG_KEY] = null
            }
        }
    }

    BackHandler(enabled = !onHomeTab || parentNavController.previousBackStackEntry != null) {
        onBackClicked()
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surface,
        bottomBar = {
            MainBottomBar(
                items = bottomItems,
                currentDestination = nestedEntry?.destination,
                onItemClick = changeTab,
                onAddClick = { parentNavController.navigate(ScanRoute) },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPaddings ->
        NavHost(
            navController = nestedNavController,
            startDestination = MainNavItem.Home,
            modifier = Modifier
                .padding(innerPaddings)
                .fillMaxSize(),
        ) {
            composable<MainNavItem.Home> {
                Home()
            }
            composable<MainNavItem.Directory> {
                Files(
                    parentNavController,
                    hiltViewModel<FilesViewModel>(),
                    shareViewModel,
                    onOpenProfile = openProfileTab,
                )
            }
            composable<MainNavItem.Share> {
                Share(
                    parentNavController,
                    shareViewModel,
                    onOpenProfile = openProfileTab,
                )
            }
            composable<MainNavItem.Profile> {
                Profile(parentNavController, hiltViewModel<ProfileViewModel>())
            }
        }
    }

}
