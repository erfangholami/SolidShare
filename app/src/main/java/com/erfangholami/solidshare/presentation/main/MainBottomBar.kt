package com.erfangholami.solidshare.presentation.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import com.erfangholami.solidshare.R
import com.erfangholami.solidshare.presentation.navigation.MainNavItem

private val NavBarHeight = 64.dp
private val AddButtonSize = 48.dp

@Composable
fun MainBottomBar(
    items: List<MainNavItem.MainNavBottomItem<*>>,
    currentDestination: NavDestination?,
    onItemClick: (route: Any) -> Unit,
    onAddClick: () -> Unit,
) {
    val bottomInset = WindowInsets.systemBars.asPaddingValues().calculateBottomPadding()
    val itemColors = NavigationBarItemDefaults.colors(
        selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
        selectedTextColor = MaterialTheme.colorScheme.primary,
        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    val centerSlot = items.size / 2

    NavigationBar(
        modifier = Modifier
            .fillMaxWidth()
            .height(NavBarHeight + bottomInset)
            .shadow(elevation = 8.dp),
    ) {
        items.forEachIndexed { index, screen ->
            if (index == centerSlot) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.Center,
                ) {
                    FilledIconButton(
                        onClick = onAddClick,
                        shape = CircleShape,
                        modifier = Modifier.size(AddButtonSize),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = stringResource(R.string.add),
                        )
                    }
                }
            }
            NavigationBarItem(
                icon = { Icon(painterResource(screen.icon), contentDescription = null) },
                label = { Text(stringResource(screen.title)) },
                selected = currentDestination?.hierarchy?.any { it.hasRoute(screen.route::class) } == true,
                onClick = { onItemClick(screen.route) },
                colors = itemColors,
            )
        }
    }
}
