package com.erfangholami.solidshare.presentation.components

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.Business
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Mail
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Work
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.erfangholami.solidshare.R
import com.erfangholami.solidshare.domain.model.PublicProfile
import com.erfangholami.solidshare.presentation.theme.AppTheme

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

        Spacer(Modifier.height(16.dp))

        ProfileDetails(profile = profile)
    }
}

@Composable
private fun ProfileDetails(profile: PublicProfile, modifier: Modifier = Modifier) {
    val rows = buildList {
        profile.givenName?.takeIf { it.isNotBlank() }?.let {
            add(DetailEntry(Icons.Outlined.Person, R.string.profile_field_given_name, it))
        }
        profile.familyName?.takeIf { it.isNotBlank() }?.let {
            add(DetailEntry(Icons.Outlined.Badge, R.string.profile_field_family_name, it))
        }
        profile.role?.takeIf { it.isNotBlank() }?.let {
            add(DetailEntry(Icons.Outlined.Work, R.string.profile_field_role, it))
        }
        profile.organization?.takeIf { it.isNotBlank() }?.let {
            add(DetailEntry(Icons.Outlined.Business, R.string.profile_field_organization, it))
        }
        profile.emails.forEach {
            add(
                DetailEntry(
                    Icons.Outlined.Mail,
                    R.string.profile_field_email,
                    it.removePrefix("mailto:")
                )
            )
        }
        profile.phones.forEach {
            add(
                DetailEntry(
                    Icons.Outlined.Phone,
                    R.string.profile_field_phone,
                    it.removePrefix("tel:")
                )
            )
        }
        add(DetailEntry(Icons.Outlined.Public, R.string.profile_field_webid, profile.webId))
        profile.oidcIssuer?.takeIf { it.isNotBlank() }?.let {
            add(DetailEntry(Icons.Outlined.Cloud, R.string.profile_field_pod_provider, it))
        }
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column {
            rows.forEachIndexed { index, entry ->
                DetailRow(
                    icon = entry.icon,
                    label = stringResource(entry.labelRes),
                    value = entry.value,
                )
                if (index < rows.lastIndex) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailRow(icon: ImageVector, label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

private data class DetailEntry(
    val icon: ImageVector,
    @param:StringRes val labelRes: Int,
    val value: String,
)

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun ProfileHeaderPreview() {
    AppTheme {
        ProfileHeader(profile = PreviewSamples.profile())
    }
}

@Preview(showBackground = true, widthDp = 360, name = "Minimal")
@Composable
private fun ProfileHeaderMinimalPreview() {
    AppTheme {
        ProfileHeader(
            profile = PreviewSamples.profile(
                name = "Jordan Lee",
                givenName = null,
                familyName = null,
                emails = emptyList(),
                phones = emptyList(),
                organization = null,
                role = null,
                oidcIssuer = null,
            ),
        )
    }
}
