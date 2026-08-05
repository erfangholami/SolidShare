package com.erfangholami.solidshare.domain.error

/**
 * A failure already turned into words, ready for a screen to render.
 *
 * Screens hold this instead of a bare `String` so that a banner can show the headline and the
 * explanation separately, a snackbar can show [summary], and both can offer the same [action]
 * without re-deriving it from the throwable.
 *
 * @property title short headline for the attempted operation, e.g. "Couldn't create the folder".
 * @property message one sentence naming the cause and, where there is one, the way out.
 * @property action the single most useful thing the user can do next, if any.
 * @property error the classification behind the words — for branching and telemetry, not display.
 * @property operation what was being attempted.
 */
data class UiError(
    val title: String,
    val message: String,
    val action: ErrorAction? = null,
    val error: AppError = AppError.Unexpected(),
    val operation: AppOperation = AppOperation.GENERIC,
) {
    /** Headline and explanation as one string, for snackbars and other single-slot surfaces. */
    val summary: String get() = "$title. $message"

    /** Developer-facing detail behind [message]; present only when the source carried one. */
    val diagnostic: String? get() = error.diagnostic
}

/** The one recovery a [UiError] offers, chosen so a screen never has to guess. */
sealed interface ErrorAction {

    /** Repeat the same call unchanged. */
    data object Retry : ErrorAction

    /** Send the user to sign-in, because no usable session exists. */
    data object SignIn : ErrorAction

    /** Ask the resource's owner for access via a `solidshare:AccessRequest`. */
    data class RequestAccess(
        val resourceUri: String,
        val ownerWebId: String?,
    ) : ErrorAction
}
