package com.erfangholami.solidshare.presentation.contacts

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.TheaterComedy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.erfangholami.solidshare.R
import com.erfangholami.solidshare.domain.model.ContactAddress
import com.erfangholami.solidshare.domain.model.ContactAddressType
import com.erfangholami.solidshare.domain.model.ContactDetail
import com.erfangholami.solidshare.domain.model.ContactEmailType
import com.erfangholami.solidshare.domain.model.ContactGroup
import com.erfangholami.solidshare.domain.model.ContactLinkType
import com.erfangholami.solidshare.domain.model.ContactPhoneType
import com.erfangholami.solidshare.presentation.components.BlockingProgressOverlay
import com.erfangholami.solidshare.presentation.components.ErrorState
import com.erfangholami.solidshare.presentation.components.LoadingState
import com.erfangholami.solidshare.presentation.components.PreviewSamples
import com.erfangholami.solidshare.presentation.components.ProfileAvatar
import com.erfangholami.solidshare.presentation.components.RowDivider
import com.erfangholami.solidshare.presentation.navigation.PublicProfileRoute
import com.erfangholami.solidshare.presentation.theme.AppTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactDetailPage(
    navController: NavController,
    viewModel: ContactDetailViewModel,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var menuOpen by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.load()
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
                title = {},
                actions = {
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(
                                Icons.Filled.MoreVert,
                                contentDescription = stringResource(R.string.more_options),
                            )
                        }
                        DropdownMenu(
                            expanded = menuOpen,
                            onDismissRequest = { menuOpen = false },
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        stringResource(R.string.delete),
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Filled.Delete,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                    )
                                },
                                onClick = {
                                    menuOpen = false
                                    showDeleteDialog = true
                                },
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
        containerColor = MaterialTheme.colorScheme.surface,
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                state.loading -> LoadingState(modifier = Modifier.align(Alignment.Center))

                state.error != null -> ErrorState(
                    message = state.error.orEmpty(),
                    modifier = Modifier.align(Alignment.Center),
                    retryLabel = stringResource(R.string.retry),
                    onRetry = { viewModel.load() },
                )

                state.contact != null -> ContactDetailContent(
                    contact = state.contact!!,
                    photo = state.photo,
                    memberGroups = state.memberGroups,
                    googleAccount = state.googleAccount,
                    onOpenProfile = { webId ->
                        navController.navigate(PublicProfileRoute(webId))
                    },
                )
            }
            if (state.busy) {
                BlockingProgressOverlay(label = stringResource(R.string.contacts_title))
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.contact_delete_title)) },
            text = {
                Text(
                    stringResource(
                        if (state.googleAccount != null) R.string.contact_delete_google_message
                        else R.string.contact_delete_message,
                        state.contact?.fullName.orEmpty(),
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        viewModel.delete { navController.popBackStack() }
                    },
                ) {
                    Text(
                        stringResource(R.string.delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ContactDetailContent(
    contact: ContactDetail,
    photo: ImageBitmap?,
    memberGroups: List<ContactGroup>,
    onOpenProfile: (String) -> Unit,
    googleAccount: String? = null,
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 32.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
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
                    webId = contact.uri,
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
                Spacer(Modifier.height(2.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val orgLine = listOfNotNull(
                contact.organization,
                contact.organizationUnit,
                contact.jobTitle,
            ).filter { it.isNotBlank() }.joinToString(" · ")
            if (orgLine.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = orgLine,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            googleAccount?.let {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.contact_synced_from_google, it),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(24.dp))

        contact.phones.forEach { phone ->
            DetailRow(
                icon = Icons.Filled.Call,
                label = phoneTypeLabel(phone.type),
                value = phone.number,
                onClick = {
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_DIAL, "tel:${phone.number}".toUri()),
                        )
                    }
                },
            )
        }
        contact.emails.forEach { email ->
            DetailRow(
                icon = Icons.Filled.Email,
                label = emailTypeLabel(email.type),
                value = email.address,
                onClick = {
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_SENDTO, "mailto:${email.address}".toUri()),
                        )
                    }
                },
            )
        }
        contact.addresses.forEach { address ->
            DetailRow(
                icon = Icons.Filled.Place,
                label = addressTypeLabel(address.type),
                value = formatAddress(address),
            )
        }
        contact.birthday?.let {
            DetailRow(
                icon = Icons.Filled.Cake,
                label = stringResource(R.string.contact_birthday_label),
                value = it,
            )
        }
        contact.anniversary?.let {
            DetailRow(
                icon = Icons.Filled.Favorite,
                label = stringResource(R.string.contact_anniversary_label),
                value = it,
            )
        }
        contact.organization?.let {
            DetailRow(
                icon = Icons.Filled.Business,
                label = stringResource(R.string.contact_organization_label),
                value = listOfNotNull(it, contact.organizationUnit).joinToString(" · "),
            )
        }
        contact.jobTitle?.let {
            DetailRow(
                icon = Icons.Filled.Badge,
                label = stringResource(R.string.contact_job_title_label),
                value = it,
            )
        }
        contact.role?.let {
            DetailRow(
                icon = Icons.Filled.TheaterComedy,
                label = stringResource(R.string.contact_role_label),
                value = it,
            )
        }
        contact.note?.let {
            DetailRow(
                icon = Icons.AutoMirrored.Filled.Notes,
                label = stringResource(R.string.contact_note_label),
                value = it,
            )
        }
        contact.links.forEach { link ->
            if (link.type == ContactLinkType.WEB_ID) {
                DetailRow(
                    icon = Icons.Filled.Person,
                    label = stringResource(R.string.contact_webid_label),
                    value = link.value,
                    onClick = { onOpenProfile(link.value) },
                )
            } else {
                DetailRow(
                    icon = Icons.Filled.Link,
                    label = linkTypeLabel(link.type),
                    value = link.value,
                )
            }
        }

        if (memberGroups.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            RowDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.Group,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(16.dp))
                Text(
                    text = stringResource(R.string.contact_groups_label),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                )
            }
            FlowRow(
                modifier = Modifier.padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                memberGroups.forEach { group ->
                    AssistChip(
                        onClick = {},
                        enabled = false,
                        label = { Text(group.name) },
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailRow(
    icon: ImageVector,
    label: String,
    value: String,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
            )
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(16.dp))
        Column {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                color = if (onClick != null) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun formatAddress(address: ContactAddress): String = listOfNotNull(
    address.street,
    listOfNotNull(address.postalCode, address.locality).joinToString(" ").ifBlank { null },
    address.region,
    address.countryName,
    address.poBox?.let { stringResource(R.string.contact_po_box_label, it) },
).joinToString("\n")

@Composable
private fun phoneTypeLabel(type: ContactPhoneType): String = stringResource(
    when (type) {
        ContactPhoneType.CELL -> R.string.contact_phone_type_mobile
        ContactPhoneType.HOME -> R.string.contact_type_home
        ContactPhoneType.WORK -> R.string.contact_type_work
        ContactPhoneType.FAX -> R.string.contact_phone_type_fax
        ContactPhoneType.PAGER -> R.string.contact_phone_type_pager
        else -> R.string.contact_phone_label
    },
)

@Composable
private fun emailTypeLabel(type: ContactEmailType): String = stringResource(
    when (type) {
        ContactEmailType.HOME -> R.string.contact_type_home
        ContactEmailType.WORK -> R.string.contact_type_work
        ContactEmailType.OTHER -> R.string.contact_email_label
    },
)

@Composable
private fun addressTypeLabel(type: ContactAddressType): String = stringResource(
    when (type) {
        ContactAddressType.HOME -> R.string.contact_type_home
        ContactAddressType.WORK -> R.string.contact_type_work
        ContactAddressType.OTHER -> R.string.contact_address_label
    },
)

@Composable
private fun linkTypeLabel(type: ContactLinkType): String = stringResource(
    when (type) {
        ContactLinkType.HOME -> R.string.contact_type_home
        ContactLinkType.WORK -> R.string.contact_type_work
        ContactLinkType.HOMEPAGE -> R.string.contact_link_type_homepage
        ContactLinkType.WEB_ID -> R.string.contact_link_type_webid
        ContactLinkType.PUBLIC_ID -> R.string.contact_link_type_publicid
    },
)

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun ContactDetailContentPreview() {
    AppTheme {
        ContactDetailContent(
            contact = PreviewSamples.contactDetail(),
            photo = null,
            memberGroups = listOf(PreviewSamples.contactGroup()),
            onOpenProfile = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 360, name = "Contact Detail Dark")
@Composable
private fun ContactDetailContentDarkPreview() {
    AppTheme(isDarkTheme = true) {
        ContactDetailContent(
            contact = PreviewSamples.contactDetail(),
            photo = null,
            memberGroups = listOf(PreviewSamples.contactGroup()),
            onOpenProfile = {},
        )
    }
}
