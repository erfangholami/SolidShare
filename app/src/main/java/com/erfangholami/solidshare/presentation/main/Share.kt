package com.erfangholami.solidshare.presentation.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.navigation.NavController

@Composable
fun Share(
    navController: NavController,
    viewModel: ShareViewModel,
) {
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        Text(
            text = "Share page",
            textAlign = TextAlign.Center
        )
    }

}