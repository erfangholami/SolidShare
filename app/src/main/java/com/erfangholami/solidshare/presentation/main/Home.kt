package com.erfangholami.solidshare.presentation.main

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.erfangholami.solidshare.R
import com.erfangholami.solidshare.domain.model.PublicProfile
import com.erfangholami.solidshare.presentation.components.PreviewSamples
import com.erfangholami.solidshare.presentation.components.ProfileAvatar
import com.erfangholami.solidshare.presentation.theme.AppTheme

@Composable
fun Home(
    viewModel: HomeViewModel,
    onOpenContacts: () -> Unit,
    onOpenWallet: (() -> Unit)? = null,
) {
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    HomeContent(
        profile = profile,
        onOpenContacts = onOpenContacts,
        onOpenWallet = onOpenWallet,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeContent(
    profile: PublicProfile?,
    onOpenContacts: () -> Unit,
    onOpenWallet: (() -> Unit)? = null,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.home),
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
                windowInsets = WindowInsets(0),
            )
        },
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            HomeGreeting(profile)
            Spacer(Modifier.height(24.dp))
            HomeHubCard(
                icon = Icons.Filled.Contacts,
                title = stringResource(R.string.home_card_contacts_title),
                subtitle = stringResource(R.string.home_card_contacts_subtitle),
                onClick = onOpenContacts,
            )
            if (onOpenWallet != null) {
                Spacer(Modifier.height(12.dp))
                HomeHubCard(
                    icon = Icons.Filled.AccountBalanceWallet,
                    title = stringResource(R.string.home_card_wallet_title),
                    subtitle = stringResource(R.string.home_card_wallet_subtitle),
                    onClick = onOpenWallet,
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun HomeGreeting(profile: PublicProfile?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            val displayName = profile?.givenName?.takeIf { it.isNotBlank() }
                ?: profile?.name?.takeIf { it.isNotBlank() }
            Text(
                text = if (displayName != null) {
                    stringResource(R.string.home_greeting, displayName)
                } else {
                    stringResource(R.string.app_name)
                },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = stringResource(R.string.home_hub_caption),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        ProfileAvatar(
            webId = profile?.webId,
            displayName = profile?.name,
            size = 48.dp,
        )
    }
}

@Composable
private fun HomeHubCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(28.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun HomePreview() {
    AppTheme {
        HomeContent(
            profile = PreviewSamples.profile(),
            onOpenContacts = {},
            onOpenWallet = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 360, name = "Home Dark")
@Composable
private fun HomeDarkPreview() {
    AppTheme(isDarkTheme = true) {
        HomeContent(
            profile = PreviewSamples.profile(),
            onOpenContacts = {},
            onOpenWallet = {},
        )
    }
}
