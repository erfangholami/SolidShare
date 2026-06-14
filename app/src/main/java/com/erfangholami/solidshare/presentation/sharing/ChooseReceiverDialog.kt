package com.erfangholami.solidshare.presentation.sharing

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.erfangholami.solidshare.R
import com.erfangholami.solidshare.domain.model.PublicProfile
import com.erfangholami.solidshare.presentation.components.AccountRow
import com.erfangholami.solidshare.presentation.components.AddAccountRow
import com.erfangholami.solidshare.presentation.components.PreviewSamples
import com.erfangholami.solidshare.presentation.navigation.AuthNavItem
import com.erfangholami.solidshare.presentation.navigation.ChooseReceiverRoute
import com.erfangholami.solidshare.presentation.navigation.ConfirmAccessRoute
import com.erfangholami.solidshare.presentation.theme.AppTheme

@Composable
fun ChooseReceiverDialog(
    navController: NavController,
    viewModel: ChooseReceiverViewModel = hiltViewModel(),
) {
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    val activeWebId by viewModel.activeWebId.collectAsStateWithLifecycle()

    ChooseReceiverContent(
        accounts = accounts,
        activeWebId = activeWebId,
        onSelect = { webId ->
            viewModel.selectReceiver(webId) {
                navController.navigate(
                    ConfirmAccessRoute(viewModel.resourceUri, viewModel.ownerWebId),
                ) {
                    popUpTo(ChooseReceiverRoute(viewModel.resourceUri, viewModel.ownerWebId)) {
                        inclusive = true
                    }
                }
            }
        },
        onAddAccount = {
            navController.navigate(AuthNavItem.Login(isAddingAccount = true))
        },
        onDismiss = { navController.popBackStack() },
    )
}

@Composable
private fun ChooseReceiverContent(
    accounts: List<PublicProfile>,
    activeWebId: String?,
    onSelect: (String) -> Unit,
    onAddAccount: () -> Unit,
    onDismiss: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
        )
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(bottom = 16.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 8.dp, top = 8.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.choose_receiver_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.choose_receiver_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.close))
                    }
                }

                Spacer(Modifier.height(8.dp))

                accounts.forEachIndexed { index, profile ->
                    AccountRow(
                        profile = profile,
                        isActive = profile.webId == activeWebId,
                        onClick = { onSelect(profile.webId) },
                    )
                    if (index < accounts.size - 1) {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                        )
                    }
                }

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                )
                AddAccountRow(onClick = onAddAccount)
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun ChooseReceiverContentPreview() {
    AppTheme {
        ChooseReceiverContent(
            accounts = PreviewSamples.profiles("alice", "ben"),
            activeWebId = PreviewSamples.webIdOf("alice"),
            onSelect = {},
            onAddAccount = {},
            onDismiss = {},
        )
    }
}
