package com.erfangh.solidshare.presentation.onboard

import android.widget.Space
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.erfangh.solidshare.R
import com.erfangh.solidshare.presentation.navigation.AuthNavItem
import com.erfangh.solidshare.presentation.navigation.StartUpNavItem

@Composable
fun Onboarding(
    navController: NavController,
    viewModel: OnboardingViewModel
) {

    val pageNumber = remember {
        mutableIntStateOf(0)
    }

    val onBackClicked: () -> Unit = {
        if(pageNumber.intValue == 0) {
            navController.popBackStack()
        } else {
            pageNumber.intValue--
        }
    }

    val onNextClicked: () -> Unit = {
        if(pageNumber.intValue == 2) {
            viewModel.onBoardingCompleted()
            navController.navigate(AuthNavItem)
        } else {
            pageNumber.intValue++
        }
    }

    BackHandler {
        onBackClicked()
    }

    Scaffold(
        modifier = Modifier.fillMaxSize()
    ) { innerPaddings ->
        OnBoardingSlide(
            pageNumber.intValue,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPaddings),
            onBackClicked = onBackClicked,
            onNextClicked = onNextClicked
        )
    }
}

@Composable
fun OnBoardingSlide(
    slideNumber: Int,
    modifier: Modifier = Modifier,
    onBackClicked: () -> Unit = {},
    onNextClicked: () -> Unit = {},
) {

    BackHandler {
        onBackClicked()
    }

    Box(
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp, 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            when (slideNumber) {
                0 -> {
                    Image(
                        painter = painterResource(R.drawable.img_onboarding_slide1),
                        contentDescription = null,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.onboarding_slide1_title),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Spacer(Modifier.height(32.dp))
                    Text(
                        text = stringResource(R.string.onboarding_slide1_description),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                1 -> {
                    Text(
                        text = stringResource(R.string.onboarding_slide2_title),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Spacer(Modifier.height(32.dp))
                    Text(
                        text = stringResource(R.string.onboarding_slide2_description),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(136.dp))
                    Image(
                        painter = painterResource(R.drawable.img_onboarding_slide2),
                        contentDescription = null,
                    )
                }

                2 -> {
                    Image(
                        painter = painterResource(R.drawable.img_onboarding_slide3),
                        contentDescription = null,
                    )
                    Spacer(Modifier.height(80.dp))
                    Text(
                        text = stringResource(R.string.onboarding_slide3_title),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Spacer(Modifier.height(32.dp))
                    Text(
                        text = stringResource(R.string.onboarding_slide3_description),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }

        when(slideNumber) {
            0, 1 -> {
                OutlinedIconButton(
                    onClick = onNextClicked,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp, 24.dp)
                ) {
                    Icon(
                        painterResource(R.drawable.ic_next),
                        contentDescription = null
                    )
                }
            }
            2 -> {
                OutlinedButton(
                    onClick = onNextClicked,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp, 24.dp)
                ) {
                    Text(
                        text = stringResource(R.string.log_in),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }

}