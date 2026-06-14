package com.erfangholami.solidshare.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.erfangholami.solidshare.presentation.theme.AppTheme

@Composable
fun BadgeAvatar(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    iconSize: Dp = size * 0.55f,
) {
    Box(
        modifier = modifier
            .size(size)
            .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(iconSize),
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun BadgeAvatarPreview() {
    AppTheme {
        Box(modifier = Modifier.size(56.dp), contentAlignment = Alignment.Center) {
            BadgeAvatar(icon = Icons.Filled.Group)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun BadgeAvatarSmallPreview() {
    AppTheme {
        Box(modifier = Modifier.size(36.dp), contentAlignment = Alignment.Center) {
            BadgeAvatar(icon = Icons.Filled.Public, size = 24.dp, iconSize = 14.dp)
        }
    }
}
