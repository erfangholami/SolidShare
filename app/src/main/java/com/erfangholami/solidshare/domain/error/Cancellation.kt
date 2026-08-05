package com.erfangholami.solidshare.domain.error

import kotlinx.coroutines.CancellationException

/**
 * Rethrows coroutine cancellation.
 *
 * `catch (e: Exception)` also catches [CancellationException], which would turn a screen leaving
 * the composition into a visible error and swallow the cancellation the coroutine machinery is
 * waiting for. Call this first in any catch block that reports to the user.
 */
fun Throwable.rethrowIfCancellation() {
    if (this is CancellationException) throw this
}
