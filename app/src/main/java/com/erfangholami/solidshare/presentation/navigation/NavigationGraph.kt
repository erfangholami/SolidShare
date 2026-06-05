package com.erfangholami.solidshare.presentation.navigation

import android.net.Uri
import android.os.Bundle
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navigation
import androidx.navigation.toRoute
import com.erfangholami.solidshare.R
import com.erfangholami.solidshare.domain.model.ContainerItem
import com.erfangholami.solidshare.presentation.container.ResourceDetailsPage
import com.erfangholami.solidshare.presentation.container.ResourceDetailsViewModel
import com.erfangholami.solidshare.presentation.container.SharedContainerPage
import com.erfangholami.solidshare.presentation.login.Login
import com.erfangholami.solidshare.presentation.login.LoginViewModel
import com.erfangholami.solidshare.presentation.main.EditProfile
import com.erfangholami.solidshare.presentation.main.EditProfileViewModel
import com.erfangholami.solidshare.presentation.main.MainPage
import com.erfangholami.solidshare.presentation.notifications.NotificationsPage
import com.erfangholami.solidshare.presentation.notifications.NotificationsViewModel
import com.erfangholami.solidshare.presentation.onboard.Onboarding
import com.erfangholami.solidshare.presentation.onboard.OnboardingViewModel
import com.erfangholami.solidshare.presentation.sharing.ManageSharingPage
import com.erfangholami.solidshare.presentation.sharing.ManageSharingViewModel
import com.erfangholami.solidshare.presentation.sharing.PublicProfilePage
import com.erfangholami.solidshare.presentation.sharing.PublicProfileViewModel
import com.erfangholami.solidshare.presentation.sharing.ConfirmAccessPage
import com.erfangholami.solidshare.presentation.sharing.ScanPage
import com.erfangholami.solidshare.presentation.sharing.ShareProfilePage
import com.erfangholami.solidshare.presentation.sharing.ShareProfileViewModel
import com.erfangholami.solidshare.presentation.startup.Startup
import com.erfangholami.solidshare.presentation.startup.StartupViewModel
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.reflect.typeOf

@Composable
fun AppNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    openNotifications: Boolean = false,
    onOpenNotificationsHandled: () -> Unit = {},
) {
    LaunchedEffect(openNotifications) {
        if (!openNotifications) return@LaunchedEffect
        if (navController.currentDestination?.isOnMain() != true) {
            navController.currentBackStackEntryFlow.first { it.destination.isOnMain() }
        }
        navController.navigate(NotificationsRoute)
        onOpenNotificationsHandled()
    }
    NavHost(
        modifier = modifier,
        navController = navController,
        startDestination = StartUpNavItem
    ) {
        onboardingGraph(navController)
        startupGraph(navController)
        authGraph(navController)
        mainGraph(navController)
        profileSubGraph(navController)
        sharedContainerGraph(navController)
        resourceDetailsGraph(navController)
        manageSharingGraph(navController)
        notificationsGraph(navController)
    }
}

private fun NavDestination.isOnMain(): Boolean =
    hierarchy.any { it.hasRoute(MainNavItem::class) }

val ContainerItemNavType = object : NavType<ContainerItem>(isNullableAllowed = false) {
    override fun get(bundle: Bundle, key: String): ContainerItem? =
        bundle.getString(key)?.let { Json.decodeFromString<ContainerItem>(it) }

    override fun parseValue(value: String): ContainerItem =
        Json.decodeFromString(Uri.decode(value))

    override fun serializeAsValue(value: ContainerItem): String =
        Uri.encode(Json.encodeToString(value))

    override fun put(bundle: Bundle, key: String, value: ContainerItem) {
        bundle.putString(key, Json.encodeToString(value))
    }

    override val name: String = "containerItem"
}

val resourceDetailsTypeMap = mapOf(typeOf<ContainerItem>() to ContainerItemNavType)

fun NavGraphBuilder.resourceDetailsGraph(navController: NavController) {
    composable<ResourceDetailsRoute>(
        typeMap = resourceDetailsTypeMap,
        enterTransition = {
            slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start)
        },
        popExitTransition = {
            slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End)
        },
    ) {
        ResourceDetailsPage(navController, hiltViewModel<ResourceDetailsViewModel>())
    }
}

fun NavGraphBuilder.manageSharingGraph(navController: NavController) {
    composable<ManageSharingRoute>(
        enterTransition = {
            slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start)
        },
        popExitTransition = {
            slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End)
        },
    ) {
        ManageSharingPage(navController, hiltViewModel<ManageSharingViewModel>())
    }
}

fun NavGraphBuilder.notificationsGraph(navController: NavController) {
    composable<NotificationsRoute>(
        enterTransition = {
            slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start)
        },
        popExitTransition = {
            slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End)
        },
    ) {
        NotificationsPage(navController, hiltViewModel<NotificationsViewModel>())
    }
}

fun NavGraphBuilder.sharedContainerGraph(navController: NavController) {
    composable<SharedContainerRoute>(
        enterTransition = {
            slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start)
        },
        popExitTransition = {
            slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End)
        },
    ) { backStackEntry ->
        val route = backStackEntry.toRoute<SharedContainerRoute>()
        SharedContainerPage(
            navController = navController,
            containerUrl = route.containerUrl,
            ownerWebId = route.ownerWebId,
        )
    }
}

fun NavGraphBuilder.profileSubGraph(navController: NavController) {
    composable<EditProfileRoute> {
        EditProfile(navController, hiltViewModel<EditProfileViewModel>())
    }
    composable<ShareProfileRoute> {
        ShareProfilePage(navController, hiltViewModel<ShareProfileViewModel>())
    }
    composable<ScanRoute> {
        ScanPage(navController)
    }
    composable<ConfirmAccessRoute> {
        ConfirmAccessPage(navController)
    }
    composable<PublicProfileRoute> {
        PublicProfilePage(navController, hiltViewModel<PublicProfileViewModel>())
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
        MainPage(navController)
    }
}

@Serializable
object ContainerRoot

@Serializable
data class ContainerNested(val containerUrl: String)

@Serializable
data class SharedContainerRoute(
    val containerUrl: String,
    val shared: Boolean = true,
    val ownerWebId: String? = null,
)

@Serializable
object EditProfileRoute

@Serializable
object ShareProfileRoute

@Serializable
object ScanRoute

@Serializable
data class ConfirmAccessRoute(
    val resourceUri: String,
    val ownerWebId: String? = null,
)

@Serializable
data class PublicProfileRoute(val webId: String)

@Serializable
data class ResourceDetailsRoute(val item: ContainerItem)

@Serializable
data class ManageSharingRoute(
    val resourceUri: String,
    val canManage: Boolean = true,
)

@Serializable
object NotificationsRoute

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
            R.drawable.ic_person,
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

