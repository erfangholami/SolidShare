package com.erfangholami.solidshare.presentation.contacts

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.compose.composable
import com.erfangholami.solidshare.presentation.navigation.AddressBooksRoute
import com.erfangholami.solidshare.presentation.navigation.ContactDetailRoute
import com.erfangholami.solidshare.presentation.navigation.ContactSharingRoute
import com.erfangholami.solidshare.presentation.navigation.ContactsMergeRoute
import com.erfangholami.solidshare.presentation.navigation.ContactsRoute
import com.erfangholami.solidshare.presentation.navigation.ContactsSettingsRoute
import com.erfangholami.solidshare.presentation.navigation.SharedContactRoute
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import com.erfangholami.solidshare.presentation.navigation.NavGraphContributor
import javax.inject.Inject

class ContactsNavGraph @Inject constructor() : NavGraphContributor {

    override fun register(builder: NavGraphBuilder, navController: NavController) {
        with(builder) {

            composable<ContactsRoute>(
                enterTransition = {
                    slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start)
                },
                popExitTransition = {
                    slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End)
                },
            ) {
                ContactsPage(navController, hiltViewModel<ContactsViewModel>())
            }
            composable<ContactDetailRoute>(
                enterTransition = {
                    slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start)
                },
                popExitTransition = {
                    slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End)
                },
            ) {
                ContactDetailPage(navController, hiltViewModel<ContactDetailViewModel>())
            }
            composable<ContactsSettingsRoute>(
                enterTransition = {
                    slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start)
                },
                popExitTransition = {
                    slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End)
                },
            ) {
                ContactsSettingsPage(navController, hiltViewModel<ContactsSettingsViewModel>())
            }
            composable<AddressBooksRoute>(
                enterTransition = {
                    slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start)
                },
                popExitTransition = {
                    slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End)
                },
            ) {
                AddressBooksPage(navController)
            }
            composable<ContactsMergeRoute>(
                enterTransition = {
                    slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start)
                },
                popExitTransition = {
                    slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End)
                },
            ) {
                ContactsMergePage(navController, hiltViewModel<ContactsMergeViewModel>())
            }
            composable<ContactSharingRoute>(
                enterTransition = {
                    slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start)
                },
                popExitTransition = {
                    slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End)
                },
            ) {
                ContactSharingPage(navController, hiltViewModel<ContactShareViewModel>())
            }
            composable<SharedContactRoute>(
                enterTransition = {
                    slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start)
                },
                popExitTransition = {
                    slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End)
                },
            ) {
                SharedContactPage(navController, hiltViewModel<SharedContactViewModel>())
            }
        }
    }
}
