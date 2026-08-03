package com.erfangholami.solidshare.presentation.wallet

import android.view.WindowManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.erfangholami.solidshare.R
import com.erfangholami.solidshare.presentation.components.EntityRow
import com.erfangholami.solidshare.presentation.components.ErrorState
import com.erfangholami.solidshare.presentation.components.ProfileAvatar
import com.erfangholami.solidshare.presentation.components.RequiresConnectionHint
import com.erfangholami.solidshare.presentation.navigation.PublicProfileRoute
import com.erfangholami.solidshare.presentation.rememberIsOnline
import com.erfangholami.solidshare.presentation.sharing.shortenWebId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharedTicketPage(
    navController: NavController,
    viewModel: SharedTicketViewModel,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val isOnline by rememberIsOnline()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it) }
    }

    val activity = remember(context) { context.findActivity() }
    DisposableEffect(activity) {
        val window = activity?.window
        val previous = window?.attributes?.screenBrightness
        window?.attributes = window?.attributes?.apply {
            screenBrightness = 1f
        }
        onDispose {
            window?.attributes = window?.attributes?.apply {
                screenBrightness = previous
                    ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.shared_ticket_title),
                        fontWeight = FontWeight.SemiBold,
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
                SharedTicketViewModel.UiState.Loading ->
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))

                SharedTicketViewModel.UiState.Offline ->
                    ErrorState(
                        message = stringResource(R.string.shared_with_offline),
                        icon = Icons.Outlined.CloudOff,
                        retryLabel = stringResource(R.string.retry),
                        onRetry = viewModel::load,
                        modifier = Modifier.align(Alignment.Center),
                    )

                is SharedTicketViewModel.UiState.Error ->
                    ErrorState(
                        message = s.message,
                        title = stringResource(R.string.entity_share_load_failed),
                        icon = null,
                        retryLabel = stringResource(R.string.retry),
                        onRetry = viewModel::load,
                        modifier = Modifier.align(Alignment.Center),
                    )

                is SharedTicketViewModel.UiState.Loaded ->
                    Column(modifier = Modifier.fillMaxSize()) {
                        viewModel.ownerWebId?.let { owner ->
                            EntityRow(
                                title = stringResource(
                                    R.string.shared_by_label,
                                    shortenWebId(owner),
                                ),
                                modifier = Modifier.padding(horizontal = 16.dp),
                                leading = {
                                    ProfileAvatar(
                                        webId = owner,
                                        displayName = null,
                                        size = 40.dp,
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .clickable {
                                                navController.navigate(PublicProfileRoute(owner))
                                            },
                                    )
                                },
                            )
                        }
                        Box(modifier = Modifier.weight(1f)) {
                            TicketDetailContent(ticket = s.ticket, visuals = s.visuals)
                        }
                        RequiresConnectionHint(
                            visible = !isOnline,
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .padding(top = 4.dp),
                        )
                        Button(
                            onClick = viewModel::addToWallet,
                            enabled = isOnline && !s.inWallet && !s.adding,
                            shape = CircleShape,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                        ) {
                            if (s.adding) {
                                CircularProgressIndicator(
                                    modifier = Modifier.padding(4.dp),
                                    strokeWidth = 2.dp,
                                )
                            } else {
                                Icon(
                                    imageVector = if (s.inWallet) {
                                        Icons.Filled.CheckCircle
                                    } else {
                                        Icons.Filled.AddCircle
                                    },
                                    contentDescription = null,
                                )
                                Text(
                                    text = stringResource(
                                        if (s.inWallet) {
                                            R.string.already_in_wallet
                                        } else {
                                            R.string.add_to_my_wallet
                                        },
                                    ),
                                    modifier = Modifier.padding(start = 8.dp),
                                )
                            }
                        }
                    }
            }
        }
    }
}
