package com.erfangholami.solidshare.presentation.container

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.erfangholami.solidshare.presentation.main.ShareViewModel

@Composable
fun SharedContainerPage(
    navController: NavController,
    containerUrl: String,
    ownerWebId: String? = null,
) {
    Scaffold(
        contentWindowInsets = WindowInsets.systemBars,
    ) { padding ->
        Container(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            viewModel = hiltViewModel<ContainerViewModel>(),
            shareViewModel = hiltViewModel<ShareViewModel>(),
            onBack = { navController.popBackStack() },
            onSharerClick = ownerWebId?.let { wid ->
                {
                    navController.navigate(
                        com.erfangholami.solidshare.presentation.navigation.PublicProfileRoute(wid),
                    )
                }
            },
            onContainerClick = { url ->
                navController.navigate(
                    com.erfangholami.solidshare.presentation.navigation.SharedContainerRoute(
                        containerUrl = url,
                        ownerWebId = ownerWebId,
                    ),
                )
            },
            onResourceInfo = { item ->
                navController.navigate(
                    com.erfangholami.solidshare.presentation.navigation.ResourceDetailsRoute(item),
                )
            },
            onManageAccess = {},
        )
    }
}
