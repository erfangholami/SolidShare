package com.erfangholami.solidshare.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.erfangholami.solidshare.R
import com.erfangholami.solidshare.domain.model.PublicProfile
import com.erfangholami.solidshare.presentation.theme.AppTheme
import com.erfangholami.solidshare.presentation.util.displayNameFor

@Composable
fun AccountSwitcherCircle(
    activeProfile: PublicProfile?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val webId = activeProfile?.webId
    val name = displayNameFor(activeProfile)

    Box(
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        ProfileAvatar(
            webId = webId,
            displayName = name,
            size = 36.dp,
        )
    }
}

@Composable
fun AccountRow(
    profile: PublicProfile,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val webId = profile.webId
    val name = displayNameFor(profile)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ProfileAvatar(webId = webId, displayName = name, size = 40.dp)
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name ?: webId,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = webId,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (isActive) {
            Spacer(Modifier.width(8.dp))
            Icon(
                Icons.Filled.Check,
                contentDescription = stringResource(R.string.active_account),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
fun AddAccountRow(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Add,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(16.dp))
        Text(
            text = stringResource(R.string.add_account),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Preview(showBackground = true, widthDp = 360, name = "Account rows")
@Composable
private fun AccountRowsPreview() {
    AppTheme {
        Surface {
            Column {
                PreviewSamples.profiles("alice", "ben").forEachIndexed { index, profile ->
                    AccountRow(
                        profile = profile,
                        isActive = index == 0,
                        onClick = {},
                    )
                }
                AddAccountRow(onClick = {})
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 360, name = "Active")
@Composable
private fun AccountSwitcherCircleActivePreview() {
    AppTheme {
        Surface {
            AccountSwitcherCircle(
                activeProfile = PreviewSamples.profile(),
                onClick = {},
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 360, name = "Empty")
@Composable
private fun AccountSwitcherCircleEmptyPreview() {
    AppTheme {
        Surface {
            AccountSwitcherCircle(
                activeProfile = null,
                onClick = {},
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}
