package com.erfangholami.solidshare.domain.error

import com.erfangholami.solidshare.R
import com.erfangholami.solidshare.util.StringProvider
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Turns a failure into the words a person reads.
 *
 * A message is assembled from two halves: [AppOperation] gives the headline for what was being
 * attempted, [AppError] gives the sentence explaining the cause and the way out. That split is
 * what keeps every message specific — "Couldn't create the folder. solidcommunity.net is out of
 * space." — without a string per screen per cause.
 *
 * A handful of pairs mean something sharper together than apart; those live in [override]. Every
 * other pair falls through to [wordingFor], and adding an [AppError] variant makes the compiler
 * demand its sentence there.
 *
 * Inject this wherever a throwable would otherwise reach a screen. Nothing outside this class
 * builds an error string from a throwable.
 */
@Singleton
class ErrorPresenter @Inject constructor(
    private val mapper: AppErrorMapper,
    private val strings: StringProvider,
) {

    /**
     * Classifies [throwable] and renders it.
     *
     * @param operation what the user was doing; supplies the headline.
     * @param subject the thing being acted on ("budget.pdf", "Alice"), named in the message where
     *   that reads better than "this item".
     * @param origin the URI the failed call targeted; supplies the host a message can name.
     * @param allowRetry `false` on screens with nothing to retry with, so no dead button appears.
     */
    fun present(
        throwable: Throwable,
        operation: AppOperation,
        subject: String? = null,
        origin: String? = null,
        allowRetry: Boolean = true,
    ): UiError = present(mapper.map(throwable, origin), operation, subject, allowRetry)

    /**
     * Classifies [throwable] without rendering it, for the few places that branch on the cause —
     * a lost-access dialog, a cache fallback — rather than showing a message.
     */
    fun classify(throwable: Throwable, origin: String? = null): AppError = mapper.map(throwable, origin)

    /** Renders an already-classified [error]. */
    fun present(
        error: AppError,
        operation: AppOperation,
        subject: String? = null,
        allowRetry: Boolean = true,
    ): UiError {
        val wording = override(error, operation, subject) ?: wordingFor(error, subject)
        return UiError(
            title = strings.getString(operation.titleRes),
            message = wording.message,
            action = wording.action.takeUnless { it == ErrorAction.Retry && !allowRetry },
            error = error,
            operation = operation,
        )
    }

    /** Headline and explanation as one line, for snackbars and other single-slot surfaces. */
    fun message(
        throwable: Throwable,
        operation: AppOperation,
        subject: String? = null,
        origin: String? = null,
    ): String = present(throwable, operation, subject, origin).summary

    private fun override(error: AppError, operation: AppOperation, subject: String?): Wording? =
        when {
            error is AppError.PermissionDenied && operation.isSharingChange() -> Wording(
                strings.getString(R.string.error_permission_denied_not_owner),
            )

            error is AppError.PermissionDenied && operation.isAccessSeeking() -> Wording(
                message = error.ownerWebId
                    ?.let { strings.getString(R.string.error_permission_denied_ask_owner, it) }
                    ?: strings.getString(R.string.error_permission_denied_ask_owner_unknown),
                action = error.resourceUri?.let { ErrorAction.RequestAccess(it, error.ownerWebId) },
            )

            error is AppError.NotFound && operation == AppOperation.CREATE_SHARE -> Wording(
                strings.getString(R.string.error_not_found_cannot_share, subject.orItem()),
            )

            error is AppError.AccessUnverified && operation == AppOperation.REBUILD_SHARE_INDEX ->
                Wording(strings.getString(R.string.error_incomplete_scan), ErrorAction.Retry)

            error is AppError.RecipientUnreachable && operation == AppOperation.REQUEST_ACCESS ->
                Wording(
                    strings.getString(
                        R.string.error_no_inbox_for_request,
                        error.recipientWebId ?: subject.orRecipient(),
                    ),
                )

            error is AppError.SignInRequired && operation == AppOperation.SIGN_IN ->
                Wording(strings.getString(R.string.error_sign_in_incomplete), ErrorAction.Retry)

            error is AppError.SessionExpired && operation == AppOperation.SIGN_IN ->
                Wording(strings.getString(R.string.error_sign_in_rejected, error.host.orPod()))

            error is AppError.NotFound && operation == AppOperation.SIGN_IN ->
                Wording(strings.getString(R.string.error_sign_in_provider_not_found))

            operation == AppOperation.SIGN_IN &&
                (error is AppError.UnreadableData || error is AppError.UnexpectedResponse) ->
                Wording(strings.getString(R.string.error_sign_in_not_a_provider, error.host.orPod()))

            error is AppError.InvalidInput && operation == AppOperation.SCAN_CODE ->
                Wording(strings.getString(R.string.error_invalid_scan))

            error is AppError.InvalidInput && operation == AppOperation.IMPORT_TICKET ->
                Wording(strings.getString(R.string.error_invalid_pass))

            else -> null
        }

    private fun wordingFor(error: AppError, subject: String?): Wording = when (error) {
        AppError.Offline -> Wording(
            strings.getString(R.string.error_offline),
            ErrorAction.Retry,
        )

        is AppError.ServerUnreachable -> Wording(
            strings.getString(R.string.error_server_unreachable, error.host.orPod()),
            ErrorAction.Retry,
        )

        is AppError.Timeout -> Wording(
            strings.getString(R.string.error_timeout, error.host.orPod()),
            ErrorAction.Retry,
        )

        is AppError.InsecureConnection -> Wording(
            strings.getString(R.string.error_insecure_connection, error.host.orPod()),
        )

        AppError.RequiresConnection -> Wording(
            strings.getString(R.string.error_requires_connection),
            ErrorAction.Retry,
        )

        AppError.SignInRequired -> Wording(
            strings.getString(R.string.error_sign_in_required),
            ErrorAction.SignIn,
        )

        is AppError.SessionExpired -> Wording(
            strings.getString(R.string.error_session_expired, error.host.orPod()),
            ErrorAction.SignIn,
        )

        AppError.NoActiveAccount -> Wording(
            strings.getString(R.string.error_no_active_account),
            ErrorAction.SignIn,
        )

        is AppError.NoPodStorage -> Wording(
            strings.getString(R.string.error_no_pod_storage),
        )

        is AppError.PermissionDenied -> Wording(
            message = error.ownerWebId
                ?.let { strings.getString(R.string.error_permission_denied_owner, it) }
                ?: strings.getString(R.string.error_permission_denied),
            action = error.resourceUri?.let { ErrorAction.RequestAccess(it, error.ownerWebId) },
        )

        is AppError.AccessUnverified -> Wording(
            strings.getString(R.string.error_access_unverified),
            ErrorAction.Retry,
        )

        is AppError.NotFound -> Wording(
            strings.getString(R.string.error_not_found, subject.orItem()),
        )

        is AppError.Conflict -> Wording(
            strings.getString(R.string.error_conflict),
        )

        is AppError.ChangedElsewhere -> Wording(
            strings.getString(R.string.error_changed_elsewhere),
            ErrorAction.Retry,
        )

        is AppError.SharingChangedElsewhere -> Wording(
            strings.getString(R.string.error_sharing_changed_elsewhere),
            ErrorAction.Retry,
        )

        is AppError.OperationNotAllowed -> Wording(
            strings.getString(R.string.error_operation_not_allowed, error.host.orPod()),
        )

        is AppError.UnsupportedAccessControl -> Wording(
            strings.getString(R.string.error_unsupported_access_control, error.host.orPod()),
        )

        is AppError.RateLimited -> Wording(
            strings.getString(R.string.error_rate_limited, error.host.orPod()),
            ErrorAction.Retry,
        )

        is AppError.ServerProblem -> Wording(
            message = error.status
                ?.let { strings.getString(R.string.error_server_problem, error.host.orPod(), it) }
                ?: strings.getString(R.string.error_server_problem_no_status, error.host.orPod()),
            action = ErrorAction.Retry,
        )

        is AppError.UnexpectedResponse -> Wording(
            message = error.status
                ?.let { strings.getString(R.string.error_unexpected_response, error.host.orPod(), it) }
                ?: strings.getString(R.string.error_unexpected_response_no_status, error.host.orPod()),
        )

        is AppError.TooLarge -> Wording(
            strings.getString(R.string.error_too_large, error.host.orPod()),
        )

        is AppError.PodStorageFull -> Wording(
            strings.getString(R.string.error_pod_storage_full, error.host.orPod()),
        )

        AppError.DeviceStorageFull -> Wording(
            strings.getString(R.string.error_device_storage_full),
        )

        is AppError.UnreadableData -> Wording(
            strings.getString(R.string.error_unreadable_data, error.host.orPod()),
        )

        is AppError.RecipientUnreachable -> Wording(
            strings.getString(
                R.string.error_recipient_no_inbox,
                error.recipientWebId ?: subject.orRecipient(),
            ),
        )

        is AppError.RecipientInboxRefused -> Wording(
            strings.getString(
                R.string.error_recipient_inbox_refused,
                error.recipientWebId ?: subject.orRecipient(),
            ),
        )

        is AppError.NotificationNotDelivered -> Wording(
            message = strings.getString(
                R.string.error_notification_not_delivered,
                error.recipientWebId ?: subject.orRecipient(),
            ),
            action = ErrorAction.Retry,
        )

        AppError.AuthenticityCheckFailed -> Wording(
            strings.getString(R.string.error_authenticity_check_failed),
        )

        AppError.Cancelled -> Wording(
            strings.getString(R.string.error_cancelled),
        )

        is AppError.LocalFileUnavailable -> Wording(
            strings.getString(R.string.error_local_file_unavailable),
        )

        is AppError.InvalidInput -> Wording(
            strings.getString(R.string.error_invalid_input),
        )

        is AppError.Unexpected -> Wording(
            strings.getString(R.string.error_unexpected),
            ErrorAction.Retry,
        )
    }

    private fun String?.orPod(): String = this ?: strings.getString(R.string.error_subject_your_pod)

    private fun String?.orItem(): String = this ?: strings.getString(R.string.error_subject_this_item)

    private fun String?.orRecipient(): String =
        this ?: strings.getString(R.string.error_subject_the_recipient)

    private data class Wording(val message: String, val action: ErrorAction? = null)
}

private fun AppOperation.isSharingChange(): Boolean = this in setOf(
    AppOperation.CREATE_SHARE,
    AppOperation.UPDATE_SHARE_ACCESS,
    AppOperation.REVOKE_SHARE,
)

private fun AppOperation.isAccessSeeking(): Boolean = this in setOf(
    AppOperation.ADD_RECEIVED_SHARE,
    AppOperation.CHECK_ACCESS,
    AppOperation.OPEN_SHARED_ITEM,
    AppOperation.COPY_SHARED_ITEM,
)
