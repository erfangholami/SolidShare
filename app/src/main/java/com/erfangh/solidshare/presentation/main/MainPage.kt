package com.erfangh.solidshare.presentation.main

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.erfangh.solidshare.presentation.navigation.MainNavItem

@Composable
fun MainPage(
    parentNavController: NavController,
    viewModel: MainViewModel
) {

    val nestedNavController = rememberNavController()

    val onBackClicked: () -> Unit = {
        if (nestedNavController.graph.findStartDestination().id == nestedNavController.currentDestination?.id) {
            if (parentNavController.previousBackStackEntry != null) {
                parentNavController.popBackStack()
            } else {
                //Exit the app
            }
        } else {
            nestedNavController.popBackStack()
        }
    }

    val bottomItems = remember {
        listOf(
            MainNavItem.MainNavBottomItem.HomeItem,
            MainNavItem.MainNavBottomItem.ShareItem,
            MainNavItem.MainNavBottomItem.AddItem,
            MainNavItem.MainNavBottomItem.DirectoryItem,
            MainNavItem.MainNavBottomItem.ProfileItem,
        )
    }

    val changeTab: (t: Any) -> Unit = { tabRoute ->
        nestedNavController.navigate(tabRoute) {

            popUpTo(nestedNavController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    BackHandler {
        onBackClicked()
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize(),
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by nestedNavController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination
                bottomItems.forEach { screen ->
                    NavigationBarItem(
                        icon = { Icon(painterResource(screen.icon), contentDescription = null) },
                        label = { Text(stringResource(screen.title)) },
                        selected = currentDestination?.hierarchy?.any { it.hasRoute(screen.route::class) } == true,
                        onClick = {
                            changeTab(screen.route)
                        }
                    )
                }
            }
        }
    ) { innerPaddings ->
        NavHost(
            navController = nestedNavController,
            startDestination = MainNavItem.Home,
            modifier = Modifier
                .systemBarsPadding()
                .fillMaxSize()
                .padding(innerPaddings),
        ) {
            composable<MainNavItem.Home> {
                Home(parentNavController, hiltViewModel<HomeViewModel>())
            }
            composable<MainNavItem.Share> {
                Share(parentNavController, hiltViewModel<ShareViewModel>())
            }
            composable<MainNavItem.Add> {
                Add(parentNavController, hiltViewModel<AddViewModel>())
            }
            composable<MainNavItem.Directory> {
                Files(parentNavController, hiltViewModel<FilesViewModel>())
            }
            composable<MainNavItem.Profile> {
                Profile(parentNavController, hiltViewModel<ProfileViewModel>())
            }
        }
    }

}