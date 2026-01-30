package com.erfangh.solidshare.presentation.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Light Theme colors
 */
val light_primary_0 = Color(0xFF4C258D)
val light_primary_10 = Color(0xFF5D2CB1)
val light_primary_20 = Color(0xFF7C46E1)
val light_primary_30 = Color(0xFFA992F3)
val light_primary_40 = Color(0xFFDED9FB)
val light_primary_50 = Color(0xFFF6F4FE)

val light_neutral_0 = Color(0xFF140926)
val light_neutral_10 = Color(0xFF726B7D)
val light_neutral_20 = Color(0xFF95909D)
val light_neutral_30 = Color(0xFFC4C1C9)
val light_neutral_40 = Color(0xFFDCDADE)
val light_neutral_50 = Color(0xFFEFEEF0)
val light_neutral_60 = Color(0xFFFFFFFF)

val light_secondary_error = Color(0xFFB50309)
val light_secondary_warning = Color(0xFFF6BC2F)
val light_secondary_success = Color(0xFF0E8345)

/**
 * Dark Theme Colors
 */
val dark_primary_0 = Color(0xFF5625B6)
val dark_primary_10 = Color(0xFF6938C9)
val dark_primary_20 = Color(0xFF8652DF)
val dark_primary_30 = Color(0xFFB196F2)
val dark_primary_40 = Color(0xFFE0DCF9)
val dark_primary_50 = Color(0xFFF7F5FE)

val dark_neutral_0 = Color(0xFF1F1F1F)
val dark_neutral_10 = Color(0xFF282828)
val dark_neutral_20 = Color(0xFF5E5E61)
val dark_neutral_30 = Color(0xFF959499)
val dark_neutral_40 = Color(0xFFABA9AF)
val dark_neutral_50 = Color(0xFFDBD9E1)
val dark_neutral_60 = Color(0xFFE8E6EE)

val dark_secondary_error = Color(0xFFF34040)
val dark_secondary_warning = Color(0xFFF0BA16)
val dark_secondary_success = Color(0xFF1D9756)

@Immutable
data class Colors(
    val bgPrimary: Color = Color.Unspecified,
    val bgSecondary: Color = Color.Unspecified,
    val bgNeutralSoft: Color = Color.Unspecified,
    val bgNeutralMedium: Color = Color.Unspecified,
    val bgAccentSoft: Color = Color.Unspecified,
    val bgAccentMedium: Color = Color.Unspecified,
    val bgDisabled: Color = Color.Unspecified,
    val bgOverlay: Color = Color.Unspecified,
    val contentPrimary: Color = Color.Unspecified,
    val contentSecondary: Color = Color.Unspecified,
    val contentTertiary: Color = Color.Unspecified,
    val contentNeutral: Color = Color.Unspecified,
    val elementPrimary: Color = Color.Unspecified,
    val elementActive: Color = Color.Unspecified,
    val elementAccent: Color = Color.Unspecified,
    val elementNeutral: Color = Color.Unspecified,
    val elementDivider: Color = Color.Unspecified,
    val elementDisabledSoft: Color = Color.Unspecified,
    val elementDisabledMedium: Color = Color.Unspecified,
    val elementDisabledStrong: Color = Color.Unspecified,
    val buttonLabelPrimary: Color = Color.Unspecified,
    val buttonLabelSecondary: Color = Color.Unspecified,
    val buttonLabelDisabledSoft: Color = Color.Unspecified,
    val buttonLabelDisabledMedium: Color = Color.Unspecified,
    val systemError: Color = Color.Unspecified,
    val systemWarning: Color = Color.Unspecified,
    val systemSuccess: Color = Color.Unspecified,
)


val LightColorScheme = Colors(
    bgPrimary = light_neutral_60,
    bgSecondary = light_primary_50,
    bgNeutralSoft = light_neutral_40,
    bgNeutralMedium = light_neutral_50,
    bgAccentSoft = light_primary_10,
    bgAccentMedium = light_primary_0,
    bgDisabled = light_primary_40,
    bgOverlay = Color(0xFF282828),
    contentPrimary = light_neutral_0,
    contentSecondary = light_neutral_10,
    contentTertiary = light_neutral_20,
    contentNeutral = light_neutral_20,
    elementPrimary = light_primary_40,
    elementActive = light_primary_30,
    elementAccent = light_primary_20,
    elementNeutral = light_neutral_40,
    elementDivider = light_neutral_50,
    elementDisabledSoft = light_primary_50,
    elementDisabledMedium = light_primary_40,
    elementDisabledStrong = light_primary_40,
    buttonLabelPrimary = light_primary_50,
    buttonLabelSecondary = light_primary_10,
    buttonLabelDisabledSoft = light_primary_50,
    buttonLabelDisabledMedium = light_primary_40,
    systemError = light_secondary_error,
    systemWarning = light_secondary_warning,
    systemSuccess = light_secondary_success,
)

val DarkColorScheme = Colors(
    bgPrimary = dark_neutral_0,
    bgSecondary = dark_neutral_10,
    bgNeutralSoft = dark_neutral_20,
    bgNeutralMedium = dark_neutral_10,
    bgAccentSoft = dark_primary_10,
    bgAccentMedium = dark_primary_0,
    bgDisabled = dark_neutral_10,
    bgOverlay = Color(0xFF000000),
    contentPrimary = dark_neutral_60,
    contentSecondary = dark_neutral_50,
    contentTertiary = dark_neutral_40,
    contentNeutral = dark_neutral_30,
    elementPrimary = dark_neutral_20,
    elementActive = dark_primary_10,
    elementAccent = dark_primary_30,
    elementNeutral = dark_neutral_10,
    elementDivider = dark_neutral_10,
    elementDisabledSoft = dark_neutral_20,
    elementDisabledMedium = dark_neutral_20,
    elementDisabledStrong = dark_neutral_10,
    buttonLabelPrimary = dark_primary_50,
    buttonLabelSecondary = dark_primary_50,
    buttonLabelDisabledSoft = dark_neutral_20,
    buttonLabelDisabledMedium = dark_neutral_20,
    systemError = dark_secondary_error,
    systemWarning = dark_secondary_warning,
    systemSuccess = dark_secondary_success,
)

val LocalColors = staticCompositionLocalOf { Colors() }

