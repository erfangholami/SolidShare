package com.erfangholami.solidshare.presentation.startup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.erfangholami.solidshare.presentation.navigation.AuthNavItem
import com.erfangholami.solidshare.presentation.navigation.MainNavItem

@Composable
fun Startup(
    navController: NavController,
    viewModel: StartupViewModel
) {
    LaunchedEffect(Unit) {
        if (viewModel.isLoggedIn()) {
            navController.navigate(MainNavItem) {
                popUpTo(navController.graph.id) {
                    inclusive = true
                }
            }
        } else {
            navController.navigate(AuthNavItem) {
                popUpTo(navController.graph.id) {
                    inclusive = true
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = "Startup")
        CircularProgressIndicator(
            modifier = Modifier
                .padding(24.dp)
                .size(32.dp)
        )
    }
}
