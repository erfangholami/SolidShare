package com.erfangholami.solidshare.presentation.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navigation
import com.erfangholami.solidshare.R
import com.erfangholami.solidshare.presentation.login.Login
import com.erfangholami.solidshare.presentation.login.LoginViewModel
import com.erfangholami.solidshare.presentation.main.MainPage
import com.erfangholami.solidshare.presentation.main.MainViewModel
import com.erfangholami.solidshare.presentation.onboard.Onboarding
import com.erfangholami.solidshare.presentation.onboard.OnboardingViewModel
import com.erfangholami.solidshare.presentation.startup.Startup
import com.erfangholami.solidshare.presentation.startup.StartupViewModel
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
        onboardingGraph(navController)
        startupGraph(navController)
        authGraph(navController)
        mainGraph(navController)
    }
}

fun NavGraphBuilder.startupGraph(navController: NavController) {
    navigation<StartUpNavItem>(StartUpNavItem.Launch) {
        composable<StartUpNavItem.Launch> {
            Startup(navController, hiltViewModel<StartupViewModel>())
        }
    }
}

fun NavGraphBuilder.onboardingGraph(navController: NavController) {
    composable<OnBoarding> {
        Onboarding(navController, hiltViewModel<OnboardingViewModel>())
    }
}

fun NavGraphBuilder.authGraph(navController: NavController) {
    navigation<AuthNavItem>(AuthNavItem.Login()) {
        composable<AuthNavItem.Login> {
            Login(navController, hiltViewModel<LoginViewModel>())
        }
    }
}

fun NavGraphBuilder.mainGraph(navController: NavController) {
    composable<MainNavItem> {
        MainPage(navController, hiltViewModel<MainViewModel>())
    }
}

@Serializable
object ContainerRoot

@Serializable
data class ContainerNested(val containerUrl: String)

@Serializable
object OnBoarding

@Serializable
object StartUpNavItem {
    @Serializable
    object Launch
}

@Serializable
object AuthNavItem {
    @Serializable
    data class Login(val isAddingAccount: Boolean = false)
}

@Serializable
object MainNavItem {

    @Serializable
    sealed class MainNavBottomItem<T : Any>(
        @field:StringRes val title: Int,
        @field:DrawableRes val icon: Int,
        val route: T,
    ) {
        @Serializable
        object HomeItem : MainNavBottomItem<Home>(
            R.string.home,
            R.drawable.ic_home,
            Home
        )

        @Serializable
        object ShareItem : MainNavBottomItem<Share>(
            R.string.share,
            R.drawable.ic_share,
            Share
        )

        @Serializable
        object DirectoryItem : MainNavBottomItem<Directory>(
            R.string.files,
            R.drawable.ic_folder,
            Directory
        )

        @Serializable
        object ProfileItem : MainNavBottomItem<Profile>(
            R.string.profile,
            R.drawable.ic_profile,
            Profile
        )
    }

    @Serializable
    object Home

    @Serializable
    object Share

    @Serializable
    object Directory

    @Serializable
    object Profile

}

