package com.erfangholami.solidshare.presentation.main

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.erfangholami.solidshare.R
import com.erfangholami.solidshare.presentation.components.AccountSwitcherCircle
import com.erfangholami.solidshare.presentation.container.Container
import com.erfangholami.solidshare.presentation.container.ContainerViewModel
import com.erfangholami.solidshare.presentation.navigation.ContainerNested
import com.erfangholami.solidshare.presentation.navigation.ContainerRoot
import com.erfangholami.solidshare.presentation.navigation.NotificationsRoute
import com.erfangholami.solidshare.presentation.navigation.ResourceDetailsRoute
import com.erfangholami.solidshare.presentation.notifications.TopBarNotificationBell

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Files(
    navController: NavController,
    viewModel: FilesViewModel,
    shareViewModel: ShareViewModel,
    onOpenProfile: () -> Unit,
) {
    val containerNavController = rememberNavController()
    val activeWebId by viewModel.activeWebIdFlow.collectAsStateWithLifecycle()
    val activeProfile by viewModel.activeProfile.collectAsStateWithLifecycle()

    var lastSeenWebId by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(activeWebId) {
        if (lastSeenWebId != null && activeWebId != lastSeenWebId) {
            containerNavController.popBackStack(route = ContainerRoot, inclusive = false)
        }
        if (activeWebId != null) {
            lastSeenWebId = activeWebId
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.files),
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                actions = {
                    TopBarNotificationBell(
                        onClick = { navController.navigate(NotificationsRoute) },
                    )
                    AccountSwitcherCircle(
                        activeProfile = activeProfile,
                        onClick = onOpenProfile,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
                windowInsets = WindowInsets(0),
            )
        },
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        NavHost(
            navController = containerNavController,
            startDestination = ContainerRoot,
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            composable<ContainerRoot> {
                Container(
                    modifier = Modifier.fillMaxSize(),
                    viewModel = hiltViewModel<ContainerViewModel>(),
                    shareViewModel = shareViewModel,
                    onContainerClick = { url ->
                        containerNavController.navigate(ContainerNested(containerUrl = url))
                    },
                    onResourceInfo = { item ->
                        navController.navigate(ResourceDetailsRoute(item))
                    },
                )
            }
            composable<ContainerNested> {
                Container(
                    modifier = Modifier.fillMaxSize(),
                    viewModel = hiltViewModel<ContainerViewModel>(),
                    shareViewModel = shareViewModel,
                    onContainerClick = { url ->
                        containerNavController.navigate(ContainerNested(containerUrl = url))
                    },
                    onResourceInfo = { item ->
                        navController.navigate(ResourceDetailsRoute(item))
                    },
                )
            }
        }
    }
}
