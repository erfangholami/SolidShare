package com.erfangholami.solidshare.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.erfangholami.solidshare.domain.model.PublicProfile
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
