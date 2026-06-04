package com.erfangholami.solidshare.presentation.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.erfangholami.solidshare.R
import com.erfangholami.solidshare.domain.model.ProfileEdits
import com.erfangholami.solidshare.presentation.components.ProfileAvatar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfile(
    navController: NavController,
    viewModel: EditProfileViewModel,
) {
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val saveState by viewModel.saveState.collectAsStateWithLifecycle()
    val profileUpdatedMessage = stringResource(R.string.profile_updated)
    val profileUpdateFailedTemplate = stringResource(R.string.profile_update_failed)
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(saveState) {
        when (val s = saveState) {
            is EditProfileViewModel.SaveState.Success -> {
                snackbarHostState.showSnackbar(profileUpdatedMessage)
                viewModel.consumeSaveState()
                navController.popBackStack()
            }

            is EditProfileViewModel.SaveState.Error -> {
                snackbarHostState.showSnackbar(
                    profileUpdateFailedTemplate.format(s.message),
                )
                viewModel.consumeSaveState()
            }

            else -> Unit
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.edit_profile),
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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        val p = profile
        if (p == null) {
            Box(Modifier
                .fillMaxSize()
                .padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        var name by remember(p) { mutableStateOf(p.name.orEmpty()) }
        var givenName by remember(p) { mutableStateOf(p.givenName.orEmpty()) }
        var familyName by remember(p) { mutableStateOf(p.familyName.orEmpty()) }
        val email = remember(p) { p.emails.firstOrNull()?.removePrefix("mailto:").orEmpty() }
        val phone = remember(p) { p.phones.firstOrNull()?.removePrefix("tel:").orEmpty() }
        var role by remember(p) { mutableStateOf(p.role.orEmpty()) }
        var org by remember(p) { mutableStateOf(p.organization.orEmpty()) }

        val isSaving = saveState is EditProfileViewModel.SaveState.Saving

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ProfileAvatar(webId = p.webId, displayName = p.displayName, size = 88.dp)
            Spacer(Modifier.height(4.dp))
            Text(
                text = p.webId,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            LabeledField(
                label = stringResource(R.string.profile_field_name),
                value = name,
                onChange = { name = it },
                enabled = !isSaving
            )
            LabeledField(
                label = stringResource(R.string.profile_field_given_name),
                value = givenName,
                onChange = { givenName = it },
                enabled = !isSaving
            )
            LabeledField(
                label = stringResource(R.string.profile_field_family_name),
                value = familyName,
                onChange = { familyName = it },
                enabled = !isSaving
            )
            LabeledField(
                label = stringResource(R.string.profile_field_role),
                value = role,
                onChange = { role = it },
                enabled = !isSaving
            )
            LabeledField(
                label = stringResource(R.string.profile_field_organization),
                value = org,
                onChange = { org = it },
                enabled = !isSaving
            )

            if (email.isNotEmpty()) ReadOnlyField(
                label = stringResource(R.string.profile_field_email),
                value = email,
            )
            if (phone.isNotEmpty()) ReadOnlyField(
                label = stringResource(R.string.profile_field_phone),
                value = phone,
            )

            Spacer(Modifier.height(12.dp))

            Button(
                onClick = {
                    viewModel.save(
                        ProfileEdits(
                            name = name,
                            givenName = givenName,
                            familyName = familyName,
                            role = role,
                            organization = org,
                        ),
                    )
                },
                enabled = !isSaving,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text(stringResource(R.string.save))
                }
            }
        }
    }
}

@Composable
private fun LabeledField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    enabled: Boolean = true,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ReadOnlyField(label: String, value: String) {
    OutlinedTextField(
        value = value,
        onValueChange = {},
        label = { Text(label) },
        readOnly = true,
        singleLine = true,
        supportingText = { Text(stringResource(R.string.profile_field_read_only)) },
        modifier = Modifier.fillMaxWidth(),
    )
}
