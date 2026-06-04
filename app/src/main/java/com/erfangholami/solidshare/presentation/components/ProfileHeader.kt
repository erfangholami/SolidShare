package com.erfangholami.solidshare.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Mail
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Work
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.erfangholami.solidshare.domain.model.PublicProfile

@Composable
fun ProfileHeader(
    profile: PublicProfile,
    modifier: Modifier = Modifier,
    avatarSizeDp: Int = 96,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ProfileAvatar(
            webId = profile.webId,
            displayName = profile.displayName,
            size = avatarSizeDp.dp,
        )

        Spacer(Modifier.height(12.dp))

        Text(
            text = profile.displayName,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )

        Spacer(Modifier.height(6.dp))

        IdentityRow(Icons.Outlined.Public, profile.webId)

        profile.emails.firstOrNull()?.let {
            Spacer(Modifier.height(2.dp))
            IdentityRow(Icons.Outlined.Mail, it.removePrefix("mailto:"))
        }
        profile.phones.firstOrNull()?.let {
            Spacer(Modifier.height(2.dp))
            IdentityRow(Icons.Outlined.Phone, it.removePrefix("tel:"))
        }
        if (!profile.role.isNullOrBlank() || !profile.organization.isNullOrBlank()) {
            Spacer(Modifier.height(2.dp))
            val text = listOfNotNull(profile.role, profile.organization)
                .joinToString(" • ")
            IdentityRow(
                icon = if (profile.organization != null) Icons.Outlined.Work else Icons.Outlined.Badge,
                text = text,
            )
        }
        profile.oidcIssuer?.let {
            Spacer(Modifier.height(2.dp))
            IdentityRow(Icons.Outlined.Cloud, it)
        }
    }
}

@Composable
private fun IdentityRow(icon: ImageVector, text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
