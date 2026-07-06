package com.erfangholami.solidshare.presentation.contacts

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.erfangholami.solidshare.R
import com.erfangholami.solidshare.presentation.components.BlockingProgressOverlay
import com.erfangholami.solidshare.presentation.components.LoadingState
import com.erfangholami.solidshare.presentation.components.LoadingLayout
import com.erfangholami.solidshare.presentation.components.RowDivider
import com.erfangholami.solidshare.presentation.permissions.rememberPermissionGate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactsSettingsPage(
    navController: NavController,
    viewModel: ContactsSettingsViewModel,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingExport by remember { mutableStateOf<String?>(null) }

    val contactsGate = rememberPermissionGate(
        permission = Manifest.permission.READ_CONTACTS,
        required = true,
        rationaleTitle = stringResource(R.string.contacts_permission_title),
        rationaleText = stringResource(R.string.contacts_permission_rationale),
        settingsText = stringResource(R.string.contacts_permission_settings),
    )

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val text = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openInputStream(uri)
                            ?.use { it.readBytes().toString(Charsets.UTF_8) }
                    }.getOrNull()
                }
                if (text != null) viewModel.importVcf(text)
            }
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/x-vcard"),
    ) { uri ->
        val text = pendingExport
        pendingExport = null
        if (uri != null && text != null) {
            scope.launch {
                val written = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openOutputStream(uri)?.use { stream ->
                            stream.write(text.toByteArray(Charsets.UTF_8))
                        }
                    }.isSuccess
                }
                if (written) viewModel.notifyExportSaved() else viewModel.notifyExportFailed()
            }
        }
    }

    LaunchedEffect(contactsGate.isGranted) {
        if (contactsGate.isGranted) viewModel.loadAccounts()
    }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
                title = {
                    Text(
                        stringResource(R.string.contacts_settings_title),
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.surface,
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 32.dp),
            ) {
                SectionHeader(stringResource(R.string.contacts_settings_google_section))
                Text(
                    text = stringResource(R.string.contacts_settings_google_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
                Spacer(Modifier.height(8.dp))
                when {
                    !contactsGate.isGranted -> {
                        TextButton(
                            onClick = { contactsGate.run { viewModel.loadAccounts() } },
                            modifier = Modifier.padding(horizontal = 12.dp),
                        ) {
                            Text(stringResource(R.string.contacts_permission_grant))
                        }
                    }

                    state.loadingAccounts -> LoadingState(
                        modifier = Modifier.padding(horizontal = 20.dp),
                        layout = LoadingLayout.ROW,
                    )

                    state.googleAccounts.isEmpty() -> Text(
                        text = stringResource(R.string.contacts_settings_google_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    )

                    else -> state.googleAccounts.forEach { account ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = account.accountName,
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                                Text(
                                    text = stringResource(
                                        R.string.contacts_settings_google_count,
                                        account.contactCount,
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(
                                checked = account.enabled,
                                onCheckedChange = { enabled ->
                                    viewModel.onGoogleAccountToggled(
                                        account.accountName,
                                        enabled,
                                    )
                                },
                            )
                        }
                    }
                }

                SettingsRow(
                    icon = Icons.Filled.Sync,
                    title = stringResource(R.string.contacts_sync_now),
                    subtitle = stringResource(R.string.contacts_sync_now_subtitle),
                    onClick = viewModel::syncNow,
                )

                Spacer(Modifier.height(8.dp))
                RowDivider()
                SectionHeader(stringResource(R.string.contacts_settings_files_section))
                SettingsRow(
                    icon = Icons.Filled.UploadFile,
                    title = stringResource(R.string.contacts_settings_import_vcf),
                    subtitle = stringResource(R.string.contacts_settings_import_vcf_subtitle),
                    onClick = {
                        importLauncher.launch(
                            arrayOf("text/x-vcard", "text/vcard", "*/*"),
                        )
                    },
                )
                SettingsRow(
                    icon = Icons.Filled.Download,
                    title = stringResource(R.string.contacts_settings_export_vcf),
                    subtitle = stringResource(R.string.contacts_settings_export_vcf_subtitle),
                    onClick = {
                        viewModel.buildExport { text ->
                            pendingExport = text
                            exportLauncher.launch("solidshare-contacts.vcf")
                        }
                    },
                )

                Spacer(Modifier.height(16.dp))
                RowDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                ) {
                    Icon(
                        Icons.Filled.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = stringResource(R.string.contacts_settings_mirror_info),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (state.busy) {
                val progress = state.progress
                BlockingProgressOverlay(
                    label = if (progress != null) {
                        stringResource(
                            R.string.contacts_import_progress,
                            progress.first,
                            progress.second,
                        )
                    } else {
                        stringResource(R.string.contacts_settings_title)
                    },
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
    )
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(16.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
