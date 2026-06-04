package com.erfangholami.solidshare.presentation.onboard

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.erfangholami.solidshare.R
import com.erfangholami.solidshare.presentation.navigation.AuthNavItem
import kotlinx.coroutines.launch

private const val SLIDE_COUNT = 3

@Composable
fun Onboarding(
    navController: NavController,
    viewModel: OnboardingViewModel,
) {
    val pagerState = rememberPagerState(pageCount = { SLIDE_COUNT })
    val scope = rememberCoroutineScope()

    val onNext: () -> Unit = {
        if (pagerState.currentPage == SLIDE_COUNT - 1) {
            viewModel.onBoardingCompleted()
            navController.navigate(AuthNavItem)
        } else {
            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
        }
    }

    BackHandler {
        if (pagerState.currentPage == 0) {
            navController.popBackStack()
        } else {
            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            when (page) {
                0 -> SlideOne()
                1 -> SlideTwo()
                2 -> SlideThree()
            }
        }

        val bottomPadding = WindowInsets.systemBars.asPaddingValues().calculateBottomPadding()
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 24.dp, bottom = 24.dp + bottomPadding),
        ) {
            if (pagerState.currentPage < SLIDE_COUNT - 1) {
                val isOnLight =
                    pagerState.currentPage == 1
                NextArrowButton(
                    onClick = onNext,
                    contentColor = if (isOnLight) MaterialTheme.colorScheme.primary else Color.White,
                )
            } else {
                OutlinedButton(
                    onClick = onNext,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary,
                    ),
                ) {
                    Text(
                        text = stringResource(R.string.log_in),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp + bottomPadding),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            repeat(SLIDE_COUNT) { index ->
                val selected = index == pagerState.currentPage
                val color = when (pagerState.currentPage) {
                    0 -> if (selected) Color.White else Color.White.copy(alpha = 0.4f)
                    else -> if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outlineVariant
                }
                Box(
                    modifier = Modifier
                        .size(width = if (selected) 20.dp else 8.dp, height = 8.dp)
                        .clip(CircleShape)
                        .background(color),
                )
            }
        }
    }
}

@Composable
private fun SlideOne() {
    val primary = MaterialTheme.colorScheme.primary

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(primary),
    ) {
        HexagonBackdrop(tint = MaterialTheme.colorScheme.surface.copy(alpha = 0.08f))
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(48.dp))
            Image(
                painter = painterResource(R.drawable.img_onboarding_slide1),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .padding(top = 24.dp),
                contentScale = ContentScale.Fit,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = stringResource(R.string.onboarding_slide1_title),
                color = Color.White,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.onboarding_slide1_description),
                color = Color.White.copy(alpha = 0.85f),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(0.85f),
            )
            Spacer(Modifier.height(120.dp))
        }
    }
}

@Composable
private fun SlideTwo() {
    val primary = MaterialTheme.colorScheme.primary
    val surface = MaterialTheme.colorScheme.surface


    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.5f)
                    .background(primary)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = stringResource(R.string.onboarding_slide2_title),
                    color = Color.White,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.onboarding_slide2_description),
                    color = Color.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(surface),
            ) {
                Image(
                    painter = painterResource(R.drawable.img_onboarding_slide2),
                    contentDescription = null,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth(0.7f),
                    contentScale = ContentScale.Fit,
                )
            }
        }
        HexagonBackdrop(
            tint = MaterialTheme.colorScheme.surface.copy(alpha = 0.08f),
            bottomTint = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
        )
    }
}

@Composable
private fun SlideThree() {
    val bg = MaterialTheme.colorScheme.surface

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bg),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(48.dp))
            Image(
                painter = painterResource(R.drawable.img_onboarding_slide3),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth(0.75f)
                    .padding(top = 24.dp),
                contentScale = ContentScale.Fit,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = stringResource(R.string.onboarding_slide3_title),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.onboarding_slide3_description),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(0.85f),
            )
            Spacer(Modifier.height(120.dp))
        }
        HexagonBackdrop(tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
    }
}

@Composable
private fun HexagonBackdrop(
    tint: Color,
    bottomTint: Color = tint,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(R.drawable.logo),
            contentDescription = null,
            colorFilter = ColorFilter.tint(tint),
            modifier = Modifier
                .size(280.dp)
                .align(Alignment.TopEnd)
                .offset(x = 90.dp, y = (-60).dp),
        )
        Image(
            painter = painterResource(R.drawable.logo),
            contentDescription = null,
            colorFilter = ColorFilter.tint(bottomTint),
            modifier = Modifier
                .size(180.dp)
                .align(Alignment.BottomStart)
                .offset(x = (-60).dp, y = 40.dp),
        )
    }
}

@Composable
private fun NextArrowButton(
    onClick: () -> Unit,
    contentColor: Color,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(48.dp),
        colors = IconButtonDefaults.iconButtonColors(
            contentColor = contentColor,
        ),
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(contentColor.copy(alpha = 0.0f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_next),
                contentDescription = null,
                modifier = Modifier.size(28.dp),
            )
        }
    }
}

