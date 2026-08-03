package com.erfangholami.solidshare.presentation.wallet

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.compose.composable
import com.erfangholami.solidshare.presentation.navigation.SharedTicketRoute
import com.erfangholami.solidshare.presentation.navigation.TicketDetailRoute
import com.erfangholami.solidshare.presentation.navigation.TicketEditRoute
import com.erfangholami.solidshare.presentation.navigation.TicketImportRoute
import com.erfangholami.solidshare.presentation.navigation.TicketSharingRoute
import com.erfangholami.solidshare.presentation.navigation.WalletRoute
import com.erfangholami.solidshare.presentation.navigation.ticketEditTypeMap
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import com.erfangholami.solidshare.presentation.navigation.NavGraphContributor
import javax.inject.Inject

class WalletNavGraph @Inject constructor() : NavGraphContributor {

    override fun register(builder: NavGraphBuilder, navController: NavController) {
        with(builder) {

            composable<WalletRoute>(
                enterTransition = {
                    slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start)
                },
                popExitTransition = {
                    slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End)
                },
            ) {
                WalletPage(navController, hiltViewModel<WalletViewModel>())
            }
            composable<TicketDetailRoute>(
                enterTransition = {
                    slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start)
                },
                popExitTransition = {
                    slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End)
                },
            ) {
                TicketDetailPage(navController, hiltViewModel<TicketDetailViewModel>())
            }
            composable<TicketEditRoute>(
                typeMap = ticketEditTypeMap,
                enterTransition = {
                    slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start)
                },
                popExitTransition = {
                    slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End)
                },
            ) {
                TicketEditPage(navController, hiltViewModel<TicketEditViewModel>())
            }
            composable<TicketImportRoute>(
                enterTransition = {
                    slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start)
                },
                popExitTransition = {
                    slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End)
                },
            ) {
                TicketImportPage(navController, hiltViewModel<TicketImportViewModel>())
            }
            composable<TicketSharingRoute>(
                enterTransition = {
                    slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start)
                },
                popExitTransition = {
                    slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End)
                },
            ) {
                TicketSharingPage(navController, hiltViewModel<TicketShareViewModel>())
            }
            composable<SharedTicketRoute>(
                enterTransition = {
                    slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start)
                },
                popExitTransition = {
                    slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End)
                },
            ) {
                SharedTicketPage(navController, hiltViewModel<SharedTicketViewModel>())
            }
        }
    }
}
