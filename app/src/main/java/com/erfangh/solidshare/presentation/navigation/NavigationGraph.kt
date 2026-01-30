package com.erfangh.solidshare.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navigation
import com.erfangh.solidshare.presentation.startup.Startup
import kotlinx.serialization.Serializable

@Composable
fun AppNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    NavHost(
        modifier = modifier,
        navController = navController,
        startDestination = StartUpNavItem
    ) {
        startupGraph(navController)
    }
}


fun NavGraphBuilder.startupGraph(navController: NavController) {
    navigation<StartUpNavItem>(StartUpNavItem) {
        composable<StartUpNavItem> {
            Startup()
        }
    }
}

@Serializable
object StartUpNavItem