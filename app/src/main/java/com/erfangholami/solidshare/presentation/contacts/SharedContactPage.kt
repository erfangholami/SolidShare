package com.erfangholami.solidshare.presentation.contacts

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Person
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.erfangholami.solidshare.R
import com.erfangholami.solidshare.domain.model.ContactDetail
import com.erfangholami.solidshare.presentation.components.ErrorState
import com.erfangholami.solidshare.presentation.components.ProfileAvatar
import com.erfangholami.solidshare.presentation.components.RequiresConnectionHint
import com.erfangholami.solidshare.presentation.navigation.PublicProfileRoute
import com.erfangholami.solidshare.presentation.rememberIsOnline
import com.erfangholami.solidshare.presentation.sharing.shortenWebId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharedContactPage(
    navController: NavController,
    viewModel: SharedContactViewModel,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val isOnline by rememberIsOnline()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.shared_contact_title),
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
                SharedContactViewModel.UiState.Loading ->
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))

                SharedContactViewModel.UiState.Offline ->
                    ErrorState(
                        message = stringResource(R.string.shared_with_offline),
                        icon = Icons.Outlined.CloudOff,
                        retryLabel = stringResource(R.string.retry),
                        onRetry = viewModel::load,
                        modifier = Modifier.align(Alignment.Center),
                    )

                is SharedContactViewModel.UiState.Error ->
                    ErrorState(
                        message = s.message,
                        title = stringResource(R.string.entity_share_load_failed),
                        icon = null,
                        retryLabel = stringResource(R.string.retry),
                        onRetry = viewModel::load,
                        modifier = Modifier.align(Alignment.Center),
                    )

                is SharedContactViewModel.UiState.Loaded ->
                    Column(modifier = Modifier.fillMaxSize()) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp),
                        ) {
                            viewModel.ownerWebId?.let { owner ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(vertical = 8.dp),
                                ) {
                                    ProfileAvatar(
                                        webId = owner,
                                        displayName = null,
                                        size = 32.dp,
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .clickable {
                                                navController.navigate(PublicProfileRoute(owner))
                                            },
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        text = stringResource(
                                            R.string.shared_by_label,
                                            shortenWebId(owner),
                                        ),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            SharedContactContent(
                                contact = s.contact,
                                photo = s.photo,
                                onOpenProfile = {
                                    navController.navigate(PublicProfileRoute(it))
                                },
                            )
                        }
                        RequiresConnectionHint(
                            visible = !isOnline,
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .padding(top = 4.dp),
                        )
                        Button(
                            onClick = viewModel::addToContacts,
                            enabled = isOnline && !s.added && !s.adding,
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
                                    imageVector = if (s.added) {
                                        Icons.Filled.CheckCircle
                                    } else {
                                        Icons.Filled.AddCircle
                                    },
                                    contentDescription = null,
                                )
                                Text(
                                    text = stringResource(
                                        if (s.added) {
                                            R.string.contact_add_already_exists
                                        } else {
                                            R.string.add_to_my_contacts
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

@Composable
private fun SharedContactContent(
    contact: ContactDetail,
    photo: androidx.compose.ui.graphics.ImageBitmap?,
    onOpenProfile: (String) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(8.dp))
        if (photo != null) {
            Image(
                bitmap = photo,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape),
            )
        } else {
            ProfileAvatar(
                webId = contact.webId ?: contact.uri,
                displayName = contact.fullName,
                size = 96.dp,
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = contact.fullName,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        contact.nickname?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(16.dp))
        Column(modifier = Modifier.fillMaxWidth()) {
            contact.phones.forEach { SharedContactRow(Icons.Filled.Call, it.number) }
            contact.emails.forEach { SharedContactRow(Icons.Filled.Email, it.address) }
            listOfNotNull(contact.organization, contact.organizationUnit)
                .joinToString(" · ")
                .takeIf { it.isNotBlank() }
                ?.let { SharedContactRow(Icons.Filled.Business, it) }
            contact.jobTitle?.let { SharedContactRow(Icons.Filled.Badge, it) }
            contact.birthday?.let { SharedContactRow(Icons.Filled.Cake, it) }
            contact.note?.let { SharedContactRow(Icons.AutoMirrored.Filled.Notes, it) }
            contact.webId?.let { webId ->
                SharedContactRow(
                    icon = Icons.Filled.Person,
                    value = shortenWebId(webId),
                    onClick = { onOpenProfile(webId) },
                )
            }
        }
    }
}

@Composable
private fun SharedContactRow(
    icon: ImageVector,
    value: String,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
            )
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(16.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}
