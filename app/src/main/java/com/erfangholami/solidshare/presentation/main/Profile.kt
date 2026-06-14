package com.erfangholami.solidshare.presentation.main

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.erfangholami.solidshare.R
import com.erfangholami.solidshare.domain.model.PublicProfile
import com.erfangholami.solidshare.domain.model.ThemeMode
import com.erfangholami.solidshare.presentation.components.AccountRow
import com.erfangholami.solidshare.presentation.components.AddAccountRow
import com.erfangholami.solidshare.presentation.components.PreviewSamples
import com.erfangholami.solidshare.presentation.components.ProfileAvatar
import com.erfangholami.solidshare.presentation.components.ProfileHeader
import com.erfangholami.solidshare.presentation.theme.AppTheme
import com.erfangholami.solidshare.presentation.navigation.AuthNavItem
import com.erfangholami.solidshare.presentation.navigation.EditProfileRoute
import com.erfangholami.solidshare.presentation.navigation.NotificationsRoute
import com.erfangholami.solidshare.presentation.navigation.ShareProfileRoute
import com.erfangholami.solidshare.presentation.notifications.TopBarNotificationBell
import com.erfangholami.solidshare.presentation.util.displayNameFor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Profile(
    navController: NavController,
    viewModel: ProfileViewModel,
) {
    val navigateToLogin by viewModel.navigateToLogin.collectAsStateWithLifecycle()
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    val activeWebId by viewModel.activeWebId.collectAsStateWithLifecycle()
    val publicProfile by viewModel.publicProfile.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()

    var showAppearanceSheet by rememberSaveable { mutableStateOf(false) }
    var showAboutSheet by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(navigateToLogin) {
        if (navigateToLogin) {
            navController.navigate(AuthNavItem) {
                popUpTo(navController.graph.id) { inclusive = true }
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        if (viewModel.logoutLoading.value) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, top = 4.dp, end = 4.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TopBarNotificationBell(
                    onClick = { navController.navigate(NotificationsRoute) },
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                val profile = publicProfile
                if (profile != null) {
                    ProfileHeader(profile = profile)
                } else if (activeWebId.isNotEmpty()) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(
                        onClick = { navController.navigate(EditProfileRoute) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Outlined.Edit, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.edit))
                    }
                    OutlinedButton(
                        onClick = { navController.navigate(ShareProfileRoute) },
                        modifier = Modifier.weight(1.6f),
                    ) {
                        Icon(Icons.Outlined.QrCode2, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.share_digital_card))
                    }
                }

                AccountsCard(
                    accounts = accounts,
                    activeWebId = activeWebId.takeIf { it.isNotEmpty() },
                    onSelectAccount = viewModel::switchAccount,
                    onAddAccount = {
                        navController.navigate(AuthNavItem.Login(isAddingAccount = true))
                    },
                    modifier = Modifier.padding(horizontal = 16.dp),
                )

                SettingsCard(
                    onAppearanceClick = { showAppearanceSheet = true },
                    onAboutClick = { showAboutSheet = true },
                    onLogoutClick = { viewModel.logout() },
                    onLogoutAllClick = { viewModel.logoutAll() },
                    hasMultipleAccounts = accounts.size > 1,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                Spacer(Modifier.height(8.dp))
            }
        }
    }

    if (showAppearanceSheet) {
        AppearanceSheet(
            current = themeMode,
            onSelect = {
                viewModel.setThemeMode(it)
                showAppearanceSheet = false
            },
            onDismiss = { showAppearanceSheet = false },
        )
    }

    if (showAboutSheet) {
        AboutSheet(onDismiss = { showAboutSheet = false })
    }
}

@Composable
private fun AccountsCard(
    accounts: List<PublicProfile>,
    activeWebId: String?,
    onSelectAccount: (webId: String) -> Unit,
    onAddAccount: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column {
            Text(
                text = stringResource(R.string.accounts),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            )
            accounts.forEachIndexed { index, profile ->
                val webId = profile.webId
                AccountRow(
                    profile = profile,
                    isActive = webId == activeWebId,
                    onClick = { onSelectAccount(webId) },
                    enabled = webId != activeWebId,
                )
                if (index < accounts.size - 1) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            AddAccountRow(onClick = onAddAccount)
        }
    }
}

@Composable
private fun SettingsCard(
    onAppearanceClick: () -> Unit,
    onAboutClick: () -> Unit,
    onLogoutClick: () -> Unit,
    onLogoutAllClick: () -> Unit,
    hasMultipleAccounts: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column {
            SettingRow(
                icon = Icons.Outlined.DarkMode,
                label = stringResource(R.string.appearance),
                onClick = onAppearanceClick,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            SettingRow(
                icon = Icons.Outlined.Info,
                label = stringResource(R.string.about),
                onClick = onAboutClick,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            SettingRow(
                icon = Icons.AutoMirrored.Filled.Logout,
                label = stringResource(R.string.logout_active_account),
                onClick = onLogoutClick,
                tintError = true,
                showChevron = false,
            )
            if (hasMultipleAccounts) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                SettingRow(
                    icon = Icons.AutoMirrored.Filled.Logout,
                    label = stringResource(R.string.logout_all),
                    onClick = onLogoutAllClick,
                    tintError = true,
                    showChevron = false,
                )
            }
        }
    }
}

@Composable
private fun SettingRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    tintError: Boolean = false,
    showChevron: Boolean = true,
) {
    val tint = if (tintError) MaterialTheme.colorScheme.error
    else MaterialTheme.colorScheme.onSurface

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(16.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = tint,
            modifier = Modifier.weight(1f),
        )
        if (showChevron) {
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppearanceSheet(
    current: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val options = remember {
        listOf(
            ThemeMode.SYSTEM to R.string.theme_system,
            ThemeMode.LIGHT to R.string.theme_light,
            ThemeMode.DARK to R.string.theme_dark,
        )
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.padding(bottom = 16.dp)) {
            Text(
                stringResource(R.string.appearance),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            )
            options.forEach { (mode, labelRes) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(mode) }
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = mode == current, onClick = { onSelect(mode) })
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(labelRes), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AboutSheet(onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val version = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "0.0"
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                stringResource(R.string.about_app_version, version),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.about_app_description),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = onDismiss,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            ) { Text(stringResource(R.string.close)) }
        }
    }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun AccountsCardPreview() {
    AppTheme {
        Column(modifier = Modifier.padding(16.dp)) {
            AccountsCard(
                accounts = PreviewSamples.profiles("alice", "ben"),
                activeWebId = PreviewSamples.webIdOf("alice"),
                onSelectAccount = {},
                onAddAccount = {},
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun AccountRowActivePreview() {
    AppTheme {
        Surface {
            AccountRow(
                profile = PreviewSamples.profile(),
                isActive = true,
                onClick = {},
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 360, name = "Inactive")
@Composable
private fun AccountRowInactivePreview() {
    AppTheme {
        Surface {
            AccountRow(
                profile = PreviewSamples.profile(),
                isActive = false,
                onClick = {},
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun AddAccountRowPreview() {
    AppTheme {
        Surface {
            AddAccountRow(onClick = {})
        }
    }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun SettingsCardPreview() {
    AppTheme {
        Column(modifier = Modifier.padding(16.dp)) {
            SettingsCard(
                onAppearanceClick = {},
                onAboutClick = {},
                onLogoutClick = {},
                onLogoutAllClick = {},
                hasMultipleAccounts = true,
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun SettingRowPreview() {
    AppTheme {
        Surface {
            SettingRow(
                icon = Icons.Outlined.Palette,
                label = "Appearance",
                onClick = {},
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 360, name = "Error tint")
@Composable
private fun SettingRowErrorPreview() {
    AppTheme {
        Surface {
            SettingRow(
                icon = Icons.Outlined.Palette,
                label = "Log out",
                onClick = {},
                tintError = true,
            )
        }
    }
}
