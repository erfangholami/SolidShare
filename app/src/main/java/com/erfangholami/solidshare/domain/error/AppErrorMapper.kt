package com.erfangholami.solidshare.domain.error

import com.erfangholami.androidsolidservices.api.exceptions.SharingException
import com.erfangholami.androidsolidservices.shared.result.SolidError
import com.erfangholami.androidsolidservices.shared.result.SolidResultException
import com.erfangholami.solidshare.util.NetworkMonitor
import java.io.FileNotFoundException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException

/**
 * The single place a throwable becomes an [AppError].
 *
 * Everything the app can fail on arrives here: the library's
 * [SolidError] (the canonical classification of Solid Protocol responses),
 * [SharingException] from the sharing and notification pipeline, transport exceptions, and local
 * file I/O. Nothing else in the app is allowed to interpret a throwable for display.
 *
 * Two refinements happen here that no lower layer can make:
 *
 *  - **Offline versus unreachable.** The library reports both as a network error; only the app
 *    knows whether the device itself has a link, which decides between "you're offline" and
 *    "that pod isn't answering".
 *  - **Statuses the Solid Protocol leaves to the server.** 413 and 507 arrive as a generic
 *    unexpected response but mean specific, actionable things about quota and size.
 */
@Singleton
class AppErrorMapper @Inject constructor(
    private val networkMonitor: NetworkMonitor,
) {

    /**
     * Classifies [throwable].
     *
     * @param origin the resource, inbox, or content URI the failed call was aimed at. It supplies
     *   the host a message can name, and marks `content:` / `file:` work as local so a missing
     *   file is not reported as a network problem.
     */
    fun map(throwable: Throwable, origin: String? = null): AppError {
        val host = hostOf(origin)
        var current: Throwable? = throwable
        var depth = 0
        while (current != null && depth < MAX_CAUSE_DEPTH) {
            recognize(current, origin, host)?.let { return it }
            current = current.cause
            depth++
        }
        return AppError.Unexpected(throwable.diagnostic())
    }

    /** Classifies a library [error] that was never wrapped in a throwable. */
    fun map(error: SolidError, origin: String? = null): AppError = fromSolidError(error, hostOf(origin))

    private fun recognize(throwable: Throwable, origin: String?, host: String?): AppError? =
        when (throwable) {
            is CancellationException -> AppError.Cancelled
            is AppErrorException -> throwable.error
            is SolidResultException -> fromSolidError(throwable.error, host)
            is SharingException -> fromSharingException(throwable, host)
            else -> fromPlatform(throwable, origin, host)
        }

    private fun fromPlatform(throwable: Throwable, origin: String?, host: String?): AppError? = when {
        throwable.isOutOfSpace() -> AppError.DeviceStorageFull

        origin.isLocalUri() && throwable.isLocalFileFailure() ->
            AppError.LocalFileUnavailable(throwable.diagnostic())

        throwable is java.net.SocketTimeoutException ||
            throwable is java.util.concurrent.TimeoutException ->
            AppError.Timeout(host, throwable.diagnostic())

        throwable is javax.net.ssl.SSLException ->
            AppError.InsecureConnection(host, throwable.diagnostic())

        throwable is IOException -> unreachableOrOffline(host, throwable.diagnostic())

        else -> null
    }

    private fun unreachableOrOffline(host: String?, diagnostic: String?): AppError =
        if (networkMonitor.currentlyOnline()) {
            AppError.ServerUnreachable(host, diagnostic)
        } else {
            AppError.Offline
        }

    private fun fromSolidError(error: SolidError, originHost: String?): AppError {
        val host = error.hostHint() ?: originHost
        return when (error) {
            is SolidError.Unauthorized -> AppError.SessionExpired(host)
            is SolidError.NotAuthenticated -> AppError.SignInRequired
            is SolidError.Forbidden -> AppError.PermissionDenied(host = host)
            is SolidError.NotFound -> AppError.NotFound(host = host)
            is SolidError.MethodNotAllowed -> AppError.OperationNotAllowed(host)
            is SolidError.Conflict -> AppError.Conflict(host)
            is SolidError.PreconditionFailed -> AppError.ChangedElsewhere(host)
            is SolidError.RateLimited -> AppError.RateLimited(host)
            is SolidError.ServerError -> AppError.ServerProblem(host, error.status)

            is SolidError.UnexpectedResponse -> when (error.status) {
                HTTP_REQUEST_TIMEOUT -> AppError.Timeout(host, error.message)
                HTTP_PAYLOAD_TOO_LARGE -> AppError.TooLarge(host)
                HTTP_INSUFFICIENT_STORAGE -> AppError.PodStorageFull(host)
                else -> AppError.UnexpectedResponse(host, error.status)
            }

            is SolidError.Network -> unreachableOrOffline(host, error.message)

            is SolidError.Timeout -> AppError.Timeout(host, error.message)
            is SolidError.Tls -> AppError.InsecureConnection(host, error.message)
            is SolidError.Malformed -> AppError.UnreadableData(host, error.message)
            is SolidError.Cancelled -> AppError.Cancelled

            is SolidError.AccessDenied ->
                AppError.PermissionDenied(error.resourceUri, error.ownerWebId, host)

            is SolidError.AccessIndeterminate -> AppError.AccessUnverified(error.resourceUri, host)
            is SolidError.NoInbox -> AppError.RecipientUnreachable(error.targetWebId)
            is SolidError.InboxUnauthorized -> AppError.RecipientInboxRefused(host = host)
            is SolidError.InboxForbidden -> AppError.RecipientInboxRefused(host = host)
            is SolidError.NotificationDelivery -> AppError.NotificationNotDelivered(host = host)
            is SolidError.ImpersonationDetected -> AppError.AuthenticityCheckFailed
            is SolidError.StaleAcl -> AppError.SharingChangedElsewhere(host)

            is SolidError.UnsupportedAuthBackend ->
                AppError.UnsupportedAccessControl(host, error.backend)

            is SolidError.Unknown -> AppError.Unexpected(error.message)
        }
    }

    private fun fromSharingException(error: SharingException, host: String?): AppError = when (error) {
        is SharingException.AccessDenied ->
            AppError.PermissionDenied(error.resourceUri, error.ownerWebId, host)

        is SharingException.AccessIndeterminate -> AppError.AccessUnverified(error.resourceUri, host)
        is SharingException.NoInbox -> AppError.RecipientUnreachable(error.targetWebId)
        is SharingException.InboxUnauthorized -> AppError.RecipientInboxRefused(host = hostOf(error.inboxUri))
        is SharingException.InboxForbidden -> AppError.RecipientInboxRefused(host = hostOf(error.inboxUri))

        is SharingException.NotificationDelivery ->
            AppError.NotificationNotDelivered(host = hostOf(error.inboxUri))

        is SharingException.ImpersonationDetected -> AppError.AuthenticityCheckFailed
        is SharingException.StaleAcl -> AppError.SharingChangedElsewhere(hostOf(error.aclUri))

        is SharingException.UnsupportedAuthBackend ->
            AppError.UnsupportedAccessControl(hostOf(error.resourceUri), error.backend)

        is SharingException.IncompleteScan -> AppError.AccessUnverified(error.rootUri, host)
    }

    private fun SolidError.hostHint(): String? = when (this) {
        is SolidError.AccessDenied -> hostOf(resourceUri)
        is SolidError.AccessIndeterminate -> hostOf(resourceUri)
        is SolidError.InboxUnauthorized -> hostOf(inboxUri)
        is SolidError.InboxForbidden -> hostOf(inboxUri)
        is SolidError.NotificationDelivery -> hostOf(inboxUri)
        is SolidError.StaleAcl -> hostOf(aclUri)
        is SolidError.UnsupportedAuthBackend -> hostOf(resourceUri)
        else -> null
    }

    private companion object {
        const val MAX_CAUSE_DEPTH = 5
        const val HTTP_REQUEST_TIMEOUT = 408
        const val HTTP_PAYLOAD_TOO_LARGE = 413
        const val HTTP_INSUFFICIENT_STORAGE = 507
    }
}

private fun Throwable.diagnostic(): String =
    message?.takeIf { it.isNotBlank() }?.let { "${javaClass.simpleName}: $it" } ?: javaClass.simpleName

private fun Throwable.isLocalFileFailure(): Boolean =
    this is FileNotFoundException || this is SecurityException || this is IOException

private fun Throwable.isOutOfSpace(): Boolean =
    message?.contains("ENOSPC") == true || message?.contains("No space left") == true

private fun String?.isLocalUri(): Boolean =
    this != null && (startsWith("content:") || startsWith("file:") || startsWith("/"))

/**
 * The display host of [uri] — `solidcommunity.net` for
 * `https://alice.solidcommunity.net/private/notes.ttl`.
 *
 * Parsed by hand rather than through `java.net.URI`, which rejects the un-encoded characters that
 * legitimately appear in pod resource names and would throw where a `null` host is wanted.
 */
fun hostOf(uri: String?): String? {
    if (uri.isNullOrBlank()) return null
    val schemeEnd = uri.indexOf("://")
    if (schemeEnd <= 0) return null
    val start = schemeEnd + 3
    val end = uri.drop(start).indexOfFirst { it == '/' || it == '?' || it == '#' }
        .let { if (it < 0) uri.length else start + it }
    val authority = uri.substring(start, end).substringAfterLast('@')
    val host = if (authority.startsWith("[")) {
        authority.substringBefore(']').removePrefix("[")
    } else {
        authority.substringBefore(':')
    }
    return host.removePrefix("www.").takeIf { it.isNotBlank() }
}
