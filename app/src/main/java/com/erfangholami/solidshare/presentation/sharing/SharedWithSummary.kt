package com.erfangholami.solidshare.presentation.sharing

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.erfangholami.solidshare.R
import com.erfangholami.solidshare.domain.model.GivenShare
import com.erfangholami.solidshare.domain.model.ShareMode
import com.erfangholami.solidshare.domain.model.ShareReceiver
import com.erfangholami.solidshare.presentation.components.ProfileAvatar
import com.erfangholami.solidshare.presentation.main.ModeChip
import com.erfangholami.solidshare.presentation.theme.AppTheme

@Composable
fun SharedWithSummary(
    shares: List<GivenShare>,
    modifier: Modifier = Modifier,
    onManage: (() -> Unit)? = null,
    maxAvatars: Int = 4,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        SharedWithHeader(onManage = onManage)
        Spacer(Modifier.height(12.dp))
        if (shares.isEmpty()) {
            Text(
                text = stringResource(R.string.shared_with_none),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            SharedAccessGroups(shares = shares, maxAvatars = maxAvatars)
        }
    }
}

@Composable
fun SharedWithHeader(
    modifier: Modifier = Modifier,
    onManage: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.shared_with),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        if (onManage != null) {
            Text(
                text = stringResource(R.string.manage),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clickable(onClick = onManage)
                    .padding(start = 8.dp),
            )
        }
    }
}

@Composable
fun SharedAccessGroups(
    shares: List<GivenShare>,
    modifier: Modifier = Modifier,
    maxAvatars: Int = 4,
) {
    val groups = ShareMode.entries.mapNotNull { mode ->
        val receivers = shares
            .filter { it.mode == mode }
            .map { it.receiver }
            .distinctBy { it.subjectKey() }
        if (receivers.isEmpty()) null else mode to receivers
    }
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        groups.forEach { (mode, receivers) ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                AvatarStack(receivers = receivers, maxAvatars = maxAvatars)
                ModeChip(mode = mode)
            }
        }
    }
}

@Composable
private fun AvatarStack(receivers: List<ShareReceiver>, maxAvatars: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy((-8).dp)) {
        receivers.take(maxAvatars).forEach { receiver ->
            AvatarBubble {
                when (receiver) {
                    is ShareReceiver.WebIdReceiver ->
                        ProfileAvatar(webId = receiver.webId, displayName = null, size = 24.dp)

                    is ShareReceiver.GroupReceiver -> BadgeAvatar(Icons.Filled.Group)
                    ShareReceiver.Public -> BadgeAvatar(Icons.Filled.Public)
                }
            }
        }
        if (receivers.size > maxAvatars) {
            AvatarBubble {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.shared_with_overflow, receivers.size - maxAvatars),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
        }
    }
}

@Composable
private fun AvatarBubble(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
            .padding(2.dp),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
private fun BadgeAvatar(icon: ImageVector) {
    Box(
        modifier = Modifier
            .size(24.dp)
            .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

private const val PREVIEW_RESOURCE = "https://owner.solidcommunity.net/photos/trip.jpg"

private fun previewPerson(name: String, mode: ShareMode): GivenShare =
    GivenShare(
        receiver = ShareReceiver.WebIdReceiver("https://$name.solidcommunity.net/profile/card#me"),
        mode = mode,
        resourceUri = PREVIEW_RESOURCE,
    )

@Composable
private fun SharedWithPreviewContainer(
    isDark: Boolean = false,
    content: @Composable () -> Unit,
) {
    AppTheme(isDarkTheme = isDark) {
        Surface(
            color = MaterialTheme.colorScheme.background,
            modifier = Modifier.fillMaxWidth(),
        ) {
            OutlinedCard(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                    content()
                }
            }
        }
    }
}

@Preview(name = "Mixed · with Manage", showBackground = true, widthDp = 360)
@Composable
private fun SharedWithSummaryMixedPreview() {
    SharedWithPreviewContainer {
        SharedWithSummary(
            shares = listOf(
                previewPerson("alice", ShareMode.APPEND),
                previewPerson("ben", ShareMode.APPEND),
                GivenShare(ShareReceiver.Public, ShareMode.READ, PREVIEW_RESOURCE),
                previewPerson("carla", ShareMode.READ),
                previewPerson("dan", ShareMode.WRITE),
                previewPerson("erin", ShareMode.WRITE),
                previewPerson("finn", ShareMode.WRITE),
                previewPerson("gita", ShareMode.WRITE),
                previewPerson("hana", ShareMode.WRITE),
                previewPerson("ivo", ShareMode.WRITE),
            ),
            onManage = {},
        )
    }
}

@Preview(name = "Single group · no Manage", showBackground = true, widthDp = 360)
@Composable
private fun SharedWithSummaryNoManagePreview() {
    SharedWithPreviewContainer {
        SharedWithSummary(
            shares = listOf(
                previewPerson("alice", ShareMode.READ),
                previewPerson("ben", ShareMode.READ),
                previewPerson("carla", ShareMode.WRITE),
            ),
        )
    }
}

@Preview(name = "Overflow +5", showBackground = true, widthDp = 360)
@Composable
private fun SharedWithSummaryOverflowPreview() {
    SharedWithPreviewContainer {
        SharedWithSummary(
            shares = listOf("ann", "bob", "cy", "dee", "eli", "fay", "gus", "hugo", "iris")
                .map { previewPerson(it, ShareMode.READ) },
            onManage = {},
        )
    }
}

@Preview(name = "Public & group", showBackground = true, widthDp = 360)
@Composable
private fun SharedWithSummaryPublicGroupPreview() {
    SharedWithPreviewContainer {
        SharedWithSummary(
            shares = listOf(
                GivenShare(ShareReceiver.Public, ShareMode.READ, PREVIEW_RESOURCE),
                GivenShare(
                    ShareReceiver.GroupReceiver("https://example.org/groups/design#team"),
                    ShareMode.WRITE,
                    PREVIEW_RESOURCE,
                ),
                previewPerson("alice", ShareMode.WRITE),
            ),
            onManage = {},
        )
    }
}

@Preview(name = "Empty", showBackground = true, widthDp = 360)
@Composable
private fun SharedWithSummaryEmptyPreview() {
    SharedWithPreviewContainer {
        SharedWithSummary(shares = emptyList(), onManage = {})
    }
}

@Preview(name = "Mixed · dark", showBackground = true, widthDp = 360)
@Composable
private fun SharedWithSummaryDarkPreview() {
    SharedWithPreviewContainer(isDark = true) {
        SharedWithSummary(
            shares = listOf(
                previewPerson("alice", ShareMode.APPEND),
                previewPerson("ben", ShareMode.READ),
                GivenShare(ShareReceiver.Public, ShareMode.READ, PREVIEW_RESOURCE),
                previewPerson("carla", ShareMode.WRITE),
                previewPerson("dan", ShareMode.WRITE),
                previewPerson("erin", ShareMode.WRITE),
            ),
            onManage = {},
        )
    }
}
