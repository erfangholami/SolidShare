package com.erfangholami.solidshare.presentation.container

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.erfangholami.solidshare.R
import com.erfangholami.solidshare.presentation.main.ShareViewModel
import com.erfangholami.solidshare.presentation.sharing.displayNameForUri

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharedContainerPage(
    navController: NavController,
    containerUrl: String,
    ownerWebId: String? = null,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = displayNameForUri(containerUrl),
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
                windowInsets = WindowInsets.systemBars,
            )
        },
        contentWindowInsets = WindowInsets.systemBars,
    ) { padding ->
        Container(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            viewModel = hiltViewModel<ContainerViewModel>(),
            shareViewModel = hiltViewModel<ShareViewModel>(),
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
        )
    }
}
