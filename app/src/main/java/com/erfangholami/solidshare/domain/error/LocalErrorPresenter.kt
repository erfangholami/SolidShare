package com.erfangholami.solidshare.domain.error

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * The [ErrorPresenter] for composables that catch a failure themselves — the share sheets, which
 * own their submit state rather than delegating it to a ViewModel.
 *
 * Provided once at the composition root. `null` outside it, so a `@Preview` of a sheet renders
 * instead of crashing; call sites fall back to their own copy when it is absent.
 */
val LocalErrorPresenter = staticCompositionLocalOf<ErrorPresenter?> { null }
