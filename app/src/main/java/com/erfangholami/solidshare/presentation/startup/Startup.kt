package com.erfangholami.solidshare.presentation.startup

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.erfangholami.solidshare.presentation.navigation.AuthNavItem
import com.erfangholami.solidshare.presentation.navigation.MainNavItem
import com.erfangholami.solidshare.presentation.navigation.OnBoarding

@Composable
fun Startup(
    navController: NavController,
    viewModel: StartupViewModel
) {

    val settingState by viewModel.settingsState.collectAsStateWithLifecycle()
    val hasLoggedInUserState by viewModel.hasLoggedInUser.collectAsStateWithLifecycle()


    BackHandler {
        navController.popBackStack()
    }

    LaunchedEffect(settingState, hasLoggedInUserState) {
        if(hasLoggedInUserState) {
            navController.navigate(MainNavItem)
        } else {
            if(settingState.hasCompletedOnboarding) {
                navController.navigate(AuthNavItem)
            } else {
                navController.navigate(OnBoarding)
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
    ) { innerPaddings ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPaddings),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {

            Text(
                text = "Solid Share",
            )
        }
    }


}