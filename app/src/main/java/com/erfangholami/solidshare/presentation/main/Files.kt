package com.erfangholami.solidshare.presentation.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.navigation.NavController

@Composable
fun Files(
    navController: NavController,
    viewModel: FilesViewModel,
) {
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        Text(
            text = "Files page",
            textAlign = TextAlign.Center
        )
    }
    
}