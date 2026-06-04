package com.erfangholami.solidshare.presentation.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

val Primary0 = Color(0xFF000000)
val Primary10 = Color(0xFF000D5F)
val Primary20 = Color(0xFF001B95)
val Primary30 = Color(0xFF2437B2)
val Primary40 = Color(0xFF4D65FF)
val Primary50 = Color(0xFF6176FF)
val Primary60 = Color(0xFF7385FF)
val Primary70 = Color(0xFF97A4FF)
val Primary80 = Color(0xFFB6BFFC)
val Primary90 = Color(0xFFDEE0FF)
val Primary95 = Color(0xFFF0F3FF)
val Primary99 = Color(0xFFFAFAFF)
val Primary100 = Color(0xFFFFFFFF)

val Secondary0 = Color(0xFF000000)
val Secondary10 = Color(0xFF002D28)
val Secondary20 = Color(0xFF00534B)
val Secondary30 = Color(0xFF007D71)
val Secondary40 = Color(0xFF00A99A)
val Secondary50 = Color(0xFF2BD6C8)
val Secondary60 = Color(0xFF4AE3D0)
val Secondary70 = Color(0xFF5BEFDD)
val Secondary80 = Color(0xFFCBF2EC)
val Secondary90 = Color(0xFFDEFFF9)
val Secondary95 = Color(0xFFE1FFF8)
val Secondary99 = Color(0xFFFAFFFC)
val Secondary100 = Color(0xFFFFFFFF)

val Tertiary0 = Color(0xFF000000)
val Tertiary10 = Color(0xFF2E004D)
val Tertiary20 = Color(0xFF4C007B)
val Tertiary30 = Color(0xFF681B9F)
val Tertiary40 = Color(0xFF9D55D4)
val Tertiary50 = Color(0xFFA66AD4)
val Tertiary60 = Color(0xFFB970F0)
val Tertiary70 = Color(0xFFD091FF)
val Tertiary80 = Color(0xFFE1B6FF)
val Tertiary90 = Color(0xFFF3DEFF)
val Tertiary95 = Color(0xFFFBECFF)
val Tertiary99 = Color(0xFFFFFBFF)
val Tertiary100 = Color(0xFFFFFFFF)

val Error0 = Color(0xFF000000)
val Error10 = Color(0xFF410002)
val Error20 = Color(0xFF690005)
val Error30 = Color(0xFF93000A)
val Error40 = Color(0xFFBA1A1A)
val Error50 = Color(0xFFDE3730)
val Error60 = Color(0xFFFF5449)
val Error70 = Color(0xFFFF897D)
val Error80 = Color(0xFFFFB4AB)
val Error90 = Color(0xFFFFDAD6)
val Error95 = Color(0xFFFFEDEA)
val Error99 = Color(0xFFFFFBFF)
val Error100 = Color(0xFFFFFFFF)

val Neutral0 = Color(0xFF000000)
val Neutral10 = Color(0xFF1B1B1F)
val Neutral20 = Color(0xFF303034)
val Neutral30 = Color(0xFF47464A)
val Neutral40 = Color(0xFF5F5E62)
val Neutral50 = Color(0xFF78767A)
val Neutral60 = Color(0xFF929094)
val Neutral70 = Color(0xFFACAAAF)
val Neutral80 = Color(0xFFC8C5CA)
val Neutral90 = Color(0xFFE4E1E6)
val Neutral95 = Color(0xFFF3F0F4)
val Neutral99 = Color(0xFFFFFBFF)
val Neutral100 = Color(0xFFFFFFFF)

val NeutralVariant0 = Color(0xFF000000)
val NeutralVariant10 = Color(0xFF1D1A22)
val NeutralVariant20 = Color(0xFF322F37)
val NeutralVariant30 = Color(0xFF49454F)
val NeutralVariant40 = Color(0xFF605D66)
val NeutralVariant50 = Color(0xFF79747E)
val NeutralVariant60 = Color(0xFF938F99)
val NeutralVariant70 = Color(0xFFAEA9B4)
val NeutralVariant80 = Color(0xFFCAC4D0)
val NeutralVariant90 = Color(0xFFE7E0EC)
val NeutralVariant95 = Color(0xFFF1EFFA)
val NeutralVariant99 = Color(0xFFFFFBFF)
val NeutralVariant100 = Color(0xFFFFFFFF)

val SurfaceN0 = Color(0xFF000000)
val SurfaceN4 = Color(0xFF0E0E11)
val SurfaceN6 = Color(0xFF131316)
val SurfaceN10 = Color(0xFF1B1B1F)
val SurfaceN12 = Color(0xFF1F1F23)
val SurfaceN17 = Color(0xFF2A2A2D)
val SurfaceN20 = Color(0xFF303034)
val SurfaceN22 = Color(0xFF353438)
val SurfaceN24 = Color(0xFF39393C)
val SurfaceN87 = Color(0xFFDDDCDE)
val SurfaceN90 = Color(0xFFE5E4E6)
val SurfaceN92 = Color(0xFFEBEAEC)
val SurfaceN94 = Color(0xFFF0EFF1)
val SurfaceN95 = Color(0xFFF3F2F4)
val SurfaceN96 = Color(0xFFF7F5F7)
val SurfaceN98 = Color(0xFFFBFAFC)
val SurfaceN100 = Color(0xFFFFFFFF)

val lightScheme = lightColorScheme(
    primary = Primary40,
    onPrimary = Primary100,
    primaryContainer = Primary90,
    onPrimaryContainer = Primary10,
    inversePrimary = Primary80,
    secondary = Secondary40,
    onSecondary = Secondary100,
    secondaryContainer = Secondary90,
    onSecondaryContainer = Secondary10,
    tertiary = Tertiary40,
    onTertiary = Tertiary100,
    tertiaryContainer = Tertiary90,
    onTertiaryContainer = Tertiary10,
    error = Error40,
    onError = Error100,
    errorContainer = Error90,
    onErrorContainer = Error10,
    background = Neutral99,
    onBackground = Neutral10,
    surface = Neutral99,
    onSurface = Neutral10,
    surfaceVariant = NeutralVariant90,
    onSurfaceVariant = NeutralVariant30,
    surfaceTint = Primary40,
    inverseSurface = Neutral20,
    inverseOnSurface = Neutral95,
    outline = NeutralVariant50,
    outlineVariant = NeutralVariant80,
    scrim = Color.Black,
    surfaceBright = SurfaceN98,
    surfaceDim = SurfaceN87,
    surfaceContainerLowest = SurfaceN100,
    surfaceContainerLow = SurfaceN96,
    surfaceContainer = SurfaceN94,
    surfaceContainerHigh = SurfaceN92,
    surfaceContainerHighest = SurfaceN90,
)

val darkScheme = darkColorScheme(
    primary = Primary80,
    onPrimary = Primary20,
    primaryContainer = Primary30,
    onPrimaryContainer = Primary90,
    inversePrimary = Primary40,
    secondary = Secondary80,
    onSecondary = Secondary20,
    secondaryContainer = Secondary30,
    onSecondaryContainer = Secondary90,
    tertiary = Tertiary80,
    onTertiary = Tertiary20,
    tertiaryContainer = Tertiary30,
    onTertiaryContainer = Tertiary90,
    error = Error80,
    onError = Error20,
    errorContainer = Error30,
    onErrorContainer = Error90,
    background = Neutral10,
    onBackground = Neutral90,
    surface = Neutral10,
    onSurface = Neutral90,
    surfaceVariant = NeutralVariant30,
    onSurfaceVariant = NeutralVariant80,
    surfaceTint = Primary80,
    inverseSurface = Neutral90,
    inverseOnSurface = Neutral20,
    outline = NeutralVariant60,
    outlineVariant = NeutralVariant30,
    scrim = Color.Black,
    surfaceBright = SurfaceN24,
    surfaceDim = SurfaceN6,
    surfaceContainerLowest = SurfaceN4,
    surfaceContainerLow = SurfaceN10,
    surfaceContainer = SurfaceN12,
    surfaceContainerHigh = SurfaceN17,
    surfaceContainerHighest = SurfaceN22,
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
