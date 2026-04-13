package com.erfangholami.solidshare.presentation.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.erfangholami.solidshare.R
import com.erfangholami.solidshare.presentation.navigation.AuthNavItem

@Composable
fun Profile(
    navController: NavController,
    viewModel: ProfileViewModel,
) {
    val activeProfile by viewModel.activeProfile.collectAsStateWithLifecycle()
    val allProfiles by viewModel.allProfiles.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.navigateToLogin.collect {
            navController.navigate(AuthNavItem) {
                popUpTo(navController.graph.id) { inclusive = true }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = stringResource(R.string.accounts),
            style = MaterialTheme.typography.titleLarge,
        )

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(allProfiles) { profile ->
                val webId = profile.userInfo?.webId ?: return@items
                val isActive = activeProfile.userInfo?.webId == webId

                ProfileAccountCard(
                    webId = webId,
                    isActive = isActive,
                    onSwitch = { viewModel.switchAccount(webId) },
                    onLogout = { viewModel.logout(webId) }
                )
            }

            item {
                OutlinedButton(
                    onClick = { navController.navigate(AuthNavItem) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                ) {
                    Text(text = stringResource(R.string.add_account))
                }
            }
        }
    }
}

@Composable
fun ProfileAccountCard(
    webId: String,
    isActive: Boolean,
    onSwitch: () -> Unit,
    onLogout: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = if (isActive) stringResource(R.string.current_account) else stringResource(R.string.account),
                style = MaterialTheme.typography.labelSmall,
                color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = webId,
                style = MaterialTheme.typography.bodyMedium,
            )

            HorizontalDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!isActive) {
                    FilledTonalButton(
                        onClick = onSwitch,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(text = stringResource(R.string.switch_account))
                    }
                }
                TextButton(
                    onClick = onLogout,
                    modifier = if (isActive) Modifier.fillMaxWidth() else Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(text = stringResource(R.string.log_out))
                }
            }
        }
    }
}
