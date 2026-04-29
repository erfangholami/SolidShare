package com.erfangholami.solidshare.presentation.main

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.erfangholami.solidshare.presentation.container.Container
import com.erfangholami.solidshare.presentation.container.ContainerViewModel
import com.erfangholami.solidshare.presentation.navigation.ContainerNested
import com.erfangholami.solidshare.presentation.navigation.ContainerRoot

@Composable
fun Files(
    navController: NavController,
    viewModel: FilesViewModel,
) {
    val containerNavController = rememberNavController()
    val activeWebId by viewModel.activeWebIdFlow.collectAsStateWithLifecycle()

    var lastSeenWebId by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(activeWebId) {
        if (lastSeenWebId != null && activeWebId != lastSeenWebId) {
            containerNavController.popBackStack(route = ContainerRoot, inclusive = false)
        }
        if (activeWebId != null) {
            lastSeenWebId = activeWebId
        }
    }

    NavHost(
        navController = containerNavController,
        startDestination = ContainerRoot,
    ) {
        composable<ContainerRoot> {
            Container(
                modifier = Modifier.fillMaxSize(),
                viewModel = hiltViewModel<ContainerViewModel>(),
                onContainerClick = { url ->
                    containerNavController.navigate(ContainerNested(containerUrl = url))
                },
            )
        }
        composable<ContainerNested> {
            Container(
                modifier = Modifier.fillMaxSize(),
                viewModel = hiltViewModel<ContainerViewModel>(),
                onContainerClick = { url ->
                    containerNavController.navigate(ContainerNested(containerUrl = url))
                },
            )
        }
    }
}