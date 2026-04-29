package com.erfangholami.solidshare.presentation.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

//region Light colors
// Primary
val LightPrimary900 = Color(0xFF00176B)
val LightPrimary800 = Color(0xFF002D8B)
val LightPrimary700 = Color(0xFF0043AE)
val LightPrimary600 = Color(0xFF1C59CC)
val LightPrimary500 = Color(0xFF4D65FF)
val LightPrimary400 = Color(0xFF7584FF)
val LightPrimary300 = Color(0xFF9DACFF)
val LightPrimary200 = Color(0xFFC6D0FF)
val LightPrimary100 = Color(0xFFE1E7FF)
val LightPrimary050 = Color(0xFFF0F3FF)
val LightPrimary010 = Color(0xFFFAFAFF)

// Secondary
val LightSecondary900 = Color(0xFF0F3C38)
val LightSecondary800 = Color(0xFF13635A)
val LightSecondary700 = Color(0xFF189184)
val LightSecondary600 = Color(0xFF25B3A5)
val LightSecondary500 = Color(0xFF38D6C4)
val LightSecondary400 = Color(0xFF5EE3D2)
val LightSecondary300 = Color(0xFF8CF0E1)
val LightSecondary200 = Color(0xFFB3F7EE)
val LightSecondary100 = Color(0xFFD9FBF8)
val LightSecondary050 = Color(0xFFECFEFD)
val LightSecondary010 = Color(0xFFF6FEFE)

// Tertiary
val LightTertiary900 = Color(0xFF2A0052)
val LightTertiary800 = Color(0xFF450085)
val LightTertiary700 = Color(0xFF6200B3)
val LightTertiary600 = Color(0xFF8232D1)
val LightTertiary500 = Color(0xFF9D55D4)
val LightTertiary400 = Color(0xFFB980EB)
val LightTertiary300 = Color(0xFFD3A8F5)
val LightTertiary200 = Color(0xFFE8D1FA)
val LightTertiary100 = Color(0xFFF4E8FF)

// Gray
val LightGray900 = Color(0xFF1A1A1A)
val LightGray800 = Color(0xFF333333)
val LightGray700 = Color(0xFF4D4D4D)
val LightGray600 = Color(0xFF666666)
val LightGray500 = Color(0xFF808080)
val LightGray400 = Color(0xFF999999)
val LightGray300 = Color(0xFFB3B3B3)
val LightGray200 = Color(0xFFCCCCCC)
val LightGray100 = Color(0xFFE6E6E6)
val LightGray050 = Color(0xFFF2F2F2)
val LightGray010 = Color(0xFFF8F8F8)

// Functional
val LightAccent = Color(0xFFFFE86E)
val LightError = Color(0xFFF44336)
val LightSuccess = Color(0xFF4CAF50)
val LightSurfaceColor = Color(0xFFFAFAFF)

//endregion

//region Dark Colors
// Dark palette (derived from designer palette)
val DarkPrimary900 = Color(0xFFE1E7FF)
val DarkPrimary800 = Color(0xFFC6D0FF)
val DarkPrimary700 = Color(0xFF9DACFF)
val DarkPrimary600 = Color(0xFF7584FF)
val DarkPrimary500 = Color(0xFF4D65FF)
val DarkPrimary400 = Color(0xFF1C59CC)
val DarkPrimary300 = Color(0xFF0043AE)
val DarkPrimary200 = Color(0xFF002D8B)
val DarkPrimary100 = Color(0xFF00176B)
val DarkPrimary050 = Color(0xFF000E47)
val DarkPrimary010 = Color(0xFF000830)

val DarkSecondary900 = Color(0xFFD9FBF8)
val DarkSecondary800 = Color(0xFFB3F7EE)
val DarkSecondary700 = Color(0xFF8CF0E1)
val DarkSecondary600 = Color(0xFF5EE3D2)
val DarkSecondary500 = Color(0xFF38D6C4)
val DarkSecondary400 = Color(0xFF25B3A5)
val DarkSecondary300 = Color(0xFF189184)
val DarkSecondary200 = Color(0xFF13635A)
val DarkSecondary100 = Color(0xFF0F3C38)
val DarkSecondary050 = Color(0xFF0A2A26)
val DarkSecondary010 = Color(0xFF061B18)

val DarkTertiary900 = Color(0xFFF4E8FF)
val DarkTertiary800 = Color(0xFFE8D1FA)
val DarkTertiary700 = Color(0xFFD3A8F5)
val DarkTertiary600 = Color(0xFFB980EB)
val DarkTertiary500 = Color(0xFF9D55D4)
val DarkTertiary400 = Color(0xFF8232D1)
val DarkTertiary300 = Color(0xFF6200B3)
val DarkTertiary200 = Color(0xFF450085)
val DarkTertiary100 = Color(0xFF2A0052)

val DarkGray900 = Color(0xFFF8F8F8)
val DarkGray800 = Color(0xFFE6E6E6)
val DarkGray700 = Color(0xFFCCCCCC)
val DarkGray600 = Color(0xFFB3B3B3)
val DarkGray500 = Color(0xFF808080)
val DarkGray400 = Color(0xFF666666)
val DarkGray300 = Color(0xFF4D4D4D)
val DarkGray200 = Color(0xFF333333)
val DarkGray100 = Color(0xFF1A1A1A)
val DarkGray050 = Color(0xFF141414)
val DarkGray010 = Color(0xFF0E0E0E)

val DarkError = Color(0xFFFF897A)
val DarkSuccess = Color(0xFF81C784)
val DarkAccent = Color(0xFFFFE86E)

//endregion


val lightScheme = lightColorScheme(
    primary = LightPrimary600,
    onPrimary = Color.White,
    primaryContainer = LightPrimary100,
    onPrimaryContainer = LightPrimary900,
    secondary = LightSecondary700,
    onSecondary = Color.White,
    secondaryContainer = LightSecondary100,
    onSecondaryContainer = LightSecondary900,
    tertiary = LightTertiary600,
    onTertiary = Color.White,
    tertiaryContainer = LightTertiary100,
    onTertiaryContainer = LightTertiary900,
    error = LightError,
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = LightSurfaceColor,
    onBackground = LightGray900,
    surface = LightSurfaceColor,
    onSurface = LightGray900,
    surfaceVariant = LightPrimary050,
    onSurfaceVariant = LightGray600,
    outline = LightGray400,
    outlineVariant = LightGray200,
    scrim = Color.Black,
    inverseSurface = LightGray800,
    inverseOnSurface = LightGray050,
    inversePrimary = LightPrimary300,
    surfaceDim = LightGray100,
    surfaceBright = LightSurfaceColor,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = LightPrimary010,
    surfaceContainer = LightGray050,
    surfaceContainerHigh = LightGray100,
    surfaceContainerHighest = LightGray200,
)

val darkScheme = darkColorScheme(
    primary = DarkPrimary700,
    onPrimary = DarkPrimary100,
    primaryContainer = DarkPrimary300,
    onPrimaryContainer = DarkPrimary900,
    secondary = DarkSecondary700,
    onSecondary = DarkSecondary100,
    secondaryContainer = DarkSecondary300,
    onSecondaryContainer = DarkSecondary900,
    tertiary = DarkTertiary700,
    onTertiary = DarkTertiary100,
    tertiaryContainer = DarkTertiary300,
    onTertiaryContainer = DarkTertiary900,
    error = DarkError,
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF0F1118),
    onBackground = DarkGray800,
    surface = Color(0xFF0F1118),
    onSurface = DarkGray800,
    surfaceVariant = DarkGray300,
    onSurfaceVariant = DarkGray600,
    outline = DarkGray500,
    outlineVariant = DarkGray300,
    scrim = Color.Black,
    inverseSurface = DarkGray800,
    inverseOnSurface = DarkGray200,
    inversePrimary = LightPrimary600,
    surfaceDim = Color(0xFF0F1118),
    surfaceBright = DarkGray300,
    surfaceContainerLowest = Color(0xFF0A0C12),
    surfaceContainerLow = Color(0xFF141620),
    surfaceContainer = Color(0xFF1A1C26),
    surfaceContainerHigh = Color(0xFF22242E),
    surfaceContainerHighest = Color(0xFF2C2E38),
)

data class SolidShareColors(
    val folder: Color = Color(0xFFFFCA28),
    val image: Color = Color(0xFF66BB6A),
    val video: Color = Color(0xFFEF5350),
    val audio: Color = Color(0xFFAB47BC),
    val pdf: Color = Color(0xFFF4511E),
    val doc: Color = Color(0xFF42A5F5),
    val spreadsheet: Color = Color(0xFF26A69A),
    val presentation: Color = Color(0xFFFF7043),
    val code: Color = Color(0xFFFF8F00),
    val archive: Color = Color(0xFF8D6E63),
    val file: Color = Color(0xFF9E9E9E),
)

val LocalSolidShareColors = staticCompositionLocalOf { SolidShareColors() }
