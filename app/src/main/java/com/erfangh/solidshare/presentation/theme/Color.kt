package com.erfangh.solidshare.presentation.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

val primaryLight = Color(0xFF805610)
val onPrimaryLight = Color(0xFFFFFFFF)
val primaryContainerLight = Color(0xFFFFDDB3)
val onPrimaryContainerLight = Color(0xFF291800)
val secondaryLight = Color(0xFF6F5B40)
val onSecondaryLight = Color(0xFFFFFFFF)
val secondaryContainerLight = Color(0xFFFBDEBC)
val onSecondaryContainerLight = Color(0xFF271904)
val tertiaryLight = Color(0xFF51643F)
val onTertiaryLight = Color(0xFFFFFFFF)
val tertiaryContainerLight = Color(0xFFD4EABB)
val onTertiaryContainerLight = Color(0xFF102004)
val errorLight = Color(0xFFBA1A1A)
val onErrorLight = Color(0xFFFFFFFF)
val errorContainerLight = Color(0xFFFFDAD6)
val onErrorContainerLight = Color(0xFF410002)
val backgroundLight = Color(0xFFFFF8F4)
val onBackgroundLight = Color(0xFF201B13)
val surfaceLight = Color(0xFFFFF8F4)
val onSurfaceLight = Color(0xFF201B13)
val surfaceVariantLight = Color(0xFFF0E0CF)
val onSurfaceVariantLight = Color(0xFF4F4539)
val outlineLight = Color(0xFF817567)
val outlineVariantLight = Color(0xFFD3C4B4)
val scrimLight = Color(0xFF000000)
val inverseSurfaceLight = Color(0xFF362F27)
val inverseOnSurfaceLight = Color(0xFFFCEFE2)
val inversePrimaryLight = Color(0xFFF4BD6F)
val surfaceDimLight = Color(0xFFE4D8CC)
val surfaceBrightLight = Color(0xFFFFF8F4)
val surfaceContainerLowestLight = Color(0xFFFFFFFF)
val surfaceContainerLowLight = Color(0xFFFFF1E5)
val surfaceContainerLight = Color(0xFFF9ECDF)
val surfaceContainerHighLight = Color(0xFFF3E6DA)
val surfaceContainerHighestLight = Color(0xFFEDE0D4)

val primaryDark = Color(0xFFF4BD6F)
val onPrimaryDark = Color(0xFF452B00)
val primaryContainerDark = Color(0xFF633F00)
val onPrimaryContainerDark = Color(0xFFFFDDB3)
val secondaryDark = Color(0xFFDDC2A1)
val onSecondaryDark = Color(0xFF3E2D16)
val secondaryContainerDark = Color(0xFF56442A)
val onSecondaryContainerDark = Color(0xFFFBDEBC)
val tertiaryDark = Color(0xFFB8CEA1)
val onTertiaryDark = Color(0xFF243515)
val tertiaryContainerDark = Color(0xFF3A4C2A)
val onTertiaryContainerDark = Color(0xFFD4EABB)
val errorDark = Color(0xFFFFB4AB)
val onErrorDark = Color(0xFF690005)
val errorContainerDark = Color(0xFF93000A)
val onErrorContainerDark = Color(0xFFFFDAD6)
val backgroundDark = Color(0xFF18120B)
val onBackgroundDark = Color(0xFFEDE0D4)
val surfaceDark = Color(0xFF18120B)
val onSurfaceDark = Color(0xFFEDE0D4)
val surfaceVariantDark = Color(0xFF4F4539)
val onSurfaceVariantDark = Color(0xFFD3C4B4)
val outlineDark = Color(0xFF9C8F80)
val outlineVariantDark = Color(0xFF4F4539)
val scrimDark = Color(0xFF000000)
val inverseSurfaceDark = Color(0xFFEDE0D4)
val inverseOnSurfaceDark = Color(0xFF362F27)
val inversePrimaryDark = Color(0xFF805610)
val surfaceDimDark = Color(0xFF18120B)
val surfaceBrightDark = Color(0xFF3F3830)
val surfaceContainerLowestDark = Color(0xFF120D07)
val surfaceContainerLowDark = Color(0xFF201B13)
val surfaceContainerDark = Color(0xFF251F17)
val surfaceContainerHighDark = Color(0xFF2F2921)
val surfaceContainerHighestDark = Color(0xFF3B342B)


// Primary
val Primary900 = Color(0xFF00176B)
val Primary800 = Color(0xFF002D8B)
val Primary700 = Color(0xFF0043AE)
val Primary600 = Color(0xFF1C59CC)
val Primary500 = Color(0xFF4D65FF)
val Primary400 = Color(0xFF7584FF)
val Primary300 = Color(0xFF9DACFF)
val Primary200 = Color(0xFFC6D0FF)
val Primary100 = Color(0xFFE1E7FF)
val Primary050 = Color(0xFFF0F3FF)
val Primary010 = Color(0xFFFAFAFF)

// Secondary
val Secondary900 = Color(0xFF0F3C38)
val Secondary800 = Color(0xFF13635A)
val Secondary700 = Color(0xFF189184)
val Secondary600 = Color(0xFF25B3A5)
val Secondary500 = Color(0xFF38D6C4)
val Secondary400 = Color(0xFF5EE3D2)
val Secondary300 = Color(0xFF8CF0E1)
val Secondary200 = Color(0xFFB3F7EE)
val Secondary100 = Color(0xFFD9FBF8)
val Secondary050 = Color(0xFFECFEFD)
val Secondary010 = Color(0xFFF6FEFE)

// Gray (Neutrals)
val Gray900 = Color(0xFF1A1A1A)
val Gray800 = Color(0xFF333333)
val Gray700 = Color(0xFF4D4D4D)
val Gray600 = Color(0xFF666666)
val Gray500 = Color(0xFF808080)
val Gray400 = Color(0xFF999999)
val Gray300 = Color(0xFFB3B3B3)
val Gray200 = Color(0xFFCCCCCC)
val Gray100 = Color(0xFFE6E6E6)
val Gray050 = Color(0xFFF2F2F2)
val Gray010 = Color(0xFFF8F8F8)

// Tertiary (Corrected from visual swatches)
val Tertiary900 = Color(0xFF2A0052)
val Tertiary800 = Color(0xFF450085)
val Tertiary700 = Color(0xFF6200B3)
val Tertiary600 = Color(0xFF8232D1)
val Tertiary500 = Color(0xFF9D55D4) // Sampled from image
val Tertiary400 = Color(0xFFB980EB)
val Tertiary300 = Color(0xFFD3A8F5)
val Tertiary200 = Color(0xFFE8D1FA)
val Tertiary100 = Color(0xFFF4E8FF)

// Functional
val Accent = Color(0xFFFFE86E)
val Error = Color(0xFFF44336)
val Success = Color(0xFF4CAF50)
val SurfaceColor = Color(0xFFFAFAFF)

val lightColorScheme = lightColorScheme(
    primary = Primary500,
    onPrimary = Color.White,
    primaryContainer = Primary100,
    onPrimaryContainer = Primary900,

    secondary = Secondary500,
    onSecondary = Color.White,
    secondaryContainer = Secondary100,
    onSecondaryContainer = Secondary900,

    tertiary = Tertiary500,
    onTertiary = Color.White,

    error = Error,
    onError = Color.White,

    background = SurfaceColor,
    onBackground = Gray900,
    surface = SurfaceColor,
    onSurface = Gray900,

    outline = Gray500
)

val darkColorScheme = darkColorScheme(
    primary = Primary300,
    onPrimary = Primary900,
    primaryContainer = Primary800,
    onPrimaryContainer = Primary100,

    secondary = Secondary300,
    onSecondary = Secondary900,
    secondaryContainer = Secondary800,
    onSecondaryContainer = Secondary100,

    tertiary = Tertiary300,
    onTertiary = Tertiary900,

    error = Error, // In M3, Error is often adjusted for dark, but usually kept bold
    onError = Color.Black,

    background = Gray900,
    onBackground = Gray100,
    surface = Gray900,
    onSurface = Gray100,

    outline = Gray400
)


val lightScheme = lightColorScheme(
    primary = primaryLight,
    onPrimary = onPrimaryLight,
    primaryContainer = primaryContainerLight,
    onPrimaryContainer = onPrimaryContainerLight,
    secondary = secondaryLight,
    onSecondary = onSecondaryLight,
    secondaryContainer = secondaryContainerLight,
    onSecondaryContainer = onSecondaryContainerLight,
    tertiary = tertiaryLight,
    onTertiary = onTertiaryLight,
    tertiaryContainer = tertiaryContainerLight,
    onTertiaryContainer = onTertiaryContainerLight,
    error = errorLight,
    onError = onErrorLight,
    errorContainer = errorContainerLight,
    onErrorContainer = onErrorContainerLight,
    background = backgroundLight,
    onBackground = onBackgroundLight,
    surface = surfaceLight,
    onSurface = onSurfaceLight,
    surfaceVariant = surfaceVariantLight,
    onSurfaceVariant = onSurfaceVariantLight,
    outline = outlineLight,
    outlineVariant = outlineVariantLight,
    scrim = scrimLight,
    inverseSurface = inverseSurfaceLight,
    inverseOnSurface = inverseOnSurfaceLight,
    inversePrimary = inversePrimaryLight,
    surfaceDim = surfaceDimLight,
    surfaceBright = surfaceBrightLight,
    surfaceContainerLowest = surfaceContainerLowestLight,
    surfaceContainerLow = surfaceContainerLowLight,
    surfaceContainer = surfaceContainerLight,
    surfaceContainerHigh = surfaceContainerHighLight,
    surfaceContainerHighest = surfaceContainerHighestLight,
)

val darkScheme = darkColorScheme(
    primary = primaryDark,
    onPrimary = onPrimaryDark,
    primaryContainer = primaryContainerDark,
    onPrimaryContainer = onPrimaryContainerDark,
    secondary = secondaryDark,
    onSecondary = onSecondaryDark,
    secondaryContainer = secondaryContainerDark,
    onSecondaryContainer = onSecondaryContainerDark,
    tertiary = tertiaryDark,
    onTertiary = onTertiaryDark,
    tertiaryContainer = tertiaryContainerDark,
    onTertiaryContainer = onTertiaryContainerDark,
    error = errorDark,
    onError = onErrorDark,
    errorContainer = errorContainerDark,
    onErrorContainer = onErrorContainerDark,
    background = backgroundDark,
    onBackground = onBackgroundDark,
    surface = surfaceDark,
    onSurface = onSurfaceDark,
    surfaceVariant = surfaceVariantDark,
    onSurfaceVariant = onSurfaceVariantDark,
    outline = outlineDark,
    outlineVariant = outlineVariantDark,
    scrim = scrimDark,
    inverseSurface = inverseSurfaceDark,
    inverseOnSurface = inverseOnSurfaceDark,
    inversePrimary = inversePrimaryDark,
    surfaceDim = surfaceDimDark,
    surfaceBright = surfaceBrightDark,
    surfaceContainerLowest = surfaceContainerLowestDark,
    surfaceContainerLow = surfaceContainerLowDark,
    surfaceContainer = surfaceContainerDark,
    surfaceContainerHigh = surfaceContainerHighDark,
    surfaceContainerHighest = surfaceContainerHighestDark,
)

