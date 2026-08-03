package com.erfangholami.solidshare.presentation.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import javax.inject.Inject
import javax.inject.Singleton

interface NavGraphContributor {

    fun register(builder: NavGraphBuilder, navController: NavController)
}

@Singleton
class NavGraphRegistry @Inject constructor(
    private val contributors: Set<@JvmSuppressWildcards NavGraphContributor>,
) {
    fun all(): Set<NavGraphContributor> = contributors
}
