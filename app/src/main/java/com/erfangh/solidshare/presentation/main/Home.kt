package com.erfangh.solidshare.presentation.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.navigation.NavController

@Composable
fun Home(
    navController: NavController,
    viewModel: HomeViewModel,
) {

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        Text(
            text = "Home page",
            textAlign = TextAlign.Center
        )
    }

}