package com.erfangholami.solidshare.presentation.sharing

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.erfangholami.solidshare.R
import com.erfangholami.solidshare.presentation.components.ErrorState
import com.erfangholami.solidshare.presentation.components.LoadingState
import com.erfangholami.solidshare.presentation.components.ProfileHeader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PublicProfilePage(
    navController: NavController,
    viewModel: PublicProfileViewModel,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val addingToContacts by viewModel.addingToContacts.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.profile),
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = stringResource(R.string.close),
                        )
                    }
                },
                actions = {
                    if (state is PublicProfileViewModel.UiState.Success) {
                        IconButton(
                            onClick = viewModel::addToContacts,
                            enabled = !addingToContacts,
                        ) {
                            Icon(
                                Icons.Filled.PersonAdd,
                                contentDescription = stringResource(
                                    R.string.contact_add_to_contacts,
                                ),
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when (val s = state) {
                PublicProfileViewModel.UiState.Loading -> LoadingState(
                    modifier = Modifier.fillMaxSize(),
                    label = stringResource(R.string.loading_profile),
                )

                is PublicProfileViewModel.UiState.Error -> ErrorState(
                    message = s.message,
                    modifier = Modifier.fillMaxSize(),
                    title = stringResource(R.string.profile_load_failed),
                    icon = null,
                    retryLabel = stringResource(R.string.retry),
                    onRetry = viewModel::load,
                )

                is PublicProfileViewModel.UiState.Success -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 24.dp),
                ) {
                    ProfileHeader(profile = s.profile)
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}
