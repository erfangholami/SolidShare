package com.erfangholami.solidshare.domain.error

/**
 * What went wrong, classified in terms a person can act on.
 *
 * This is the app's own vocabulary of failure. Every throwable that can reach a screen — a
 * library [com.erfangholami.androidsolidservices.shared.result.SolidError], a Solid sharing
 * exception, a transport error, a local I/O error — is folded into exactly one variant by
 * [AppErrorMapper], and turned into words by [ErrorPresenter].
 *
 * Variants describe a *cause*, never an operation: "the pod refused this" rather than "the
 * upload failed". What the user was attempting is [AppOperation], and the two are combined at
 * presentation time. Keeping them apart is what stops the message catalogue from growing as
 * `operations × causes`.
 *
 * To add a cause: add a variant here and the compiler will require a message for it in
 * [ErrorPresenter]. Nothing else needs to change.
 *
 * @property host the pod or server the failure came from, when known — a message that can name
 *   the host ("inrupt.net didn't respond") is far more useful than one that can't.
 * @property diagnostic developer-facing detail. Logged, never shown as the primary message.
 * @property retryable `true` when repeating the same action unchanged may now succeed.
 */
sealed interface AppError {

    val host: String? get() = null

    val diagnostic: String? get() = null

    val retryable: Boolean get() = false

    /** The device has no usable network at all. */
    data object Offline : AppError {
        override val retryable: Boolean get() = true
    }

    /** A network is up but the pod could not be reached. */
    data class ServerUnreachable(
        override val host: String?,
        override val diagnostic: String? = null,
    ) : AppError {
        override val retryable: Boolean get() = true
    }

    /** The pod accepted the connection but did not answer in time. */
    data class Timeout(
        override val host: String?,
        override val diagnostic: String? = null,
    ) : AppError {
        override val retryable: Boolean get() = true
    }

    /** The TLS handshake or certificate check failed — the connection is not trustworthy. */
    data class InsecureConnection(
        override val host: String?,
        override val diagnostic: String? = null,
    ) : AppError

    /** An online-only operation was attempted while the app is working from its offline cache. */
    data object RequiresConnection : AppError {
        override val retryable: Boolean get() = true
    }

    /** No local session exists — the user has to sign in before this can work. */
    data object SignInRequired : AppError

    /** A session existed but the pod rejected its credentials; signing in again fixes it. */
    data class SessionExpired(override val host: String? = null) : AppError

    /** No account is selected — an internal precondition the user resolves by signing in. */
    data object NoActiveAccount : AppError

    /** The WebID profile advertises no storage, so there is nowhere to read or write. */
    data class NoPodStorage(override val host: String? = null) : AppError

    /**
     * A definitive "no" from the pod's access control (WAC or ACP).
     *
     * @property resourceUri the resource that was refused, when known.
     * @property ownerWebId the agent who could grant access, when the pod disclosed it.
     */
    data class PermissionDenied(
        val resourceUri: String? = null,
        val ownerWebId: String? = null,
        override val host: String? = null,
    ) : AppError

    /**
     * Access could not be established either way — a transient signal got in the way. Distinct
     * from [PermissionDenied]: the honest answer is "couldn't check", not "you can't".
     */
    data class AccessUnverified(
        val resourceUri: String? = null,
        override val host: String? = null,
    ) : AppError {
        override val retryable: Boolean get() = true
    }

    /** The resource is gone from the pod. */
    data class NotFound(
        val resourceUri: String? = null,
        override val host: String? = null,
    ) : AppError

    /** Something already occupies the target location, or its parent container is missing. */
    data class Conflict(override val host: String? = null) : AppError

    /** The resource changed on the pod since it was read; re-read before writing again. */
    data class ChangedElsewhere(override val host: String? = null) : AppError {
        override val retryable: Boolean get() = true
    }

    /** A concurrent edit to the resource's ACL/ACR beat this write. */
    data class SharingChangedElsewhere(override val host: String? = null) : AppError {
        override val retryable: Boolean get() = true
    }

    /** The pod does not allow this method on this resource. */
    data class OperationNotAllowed(override val host: String? = null) : AppError

    /** The pod's access-control backend is one the app cannot drive. */
    data class UnsupportedAccessControl(
        override val host: String? = null,
        val backend: String? = null,
    ) : AppError

    /** The pod is throttling the app. */
    data class RateLimited(override val host: String? = null) : AppError {
        override val retryable: Boolean get() = true
    }

    /** The pod is failing on its own side (5xx). */
    data class ServerProblem(
        override val host: String? = null,
        val status: Int? = null,
    ) : AppError {
        override val retryable: Boolean get() = true
    }

    /** A non-success status with no more specific meaning. */
    data class UnexpectedResponse(
        override val host: String? = null,
        val status: Int? = null,
    ) : AppError

    /** The payload exceeds what the pod accepts for a single resource. */
    data class TooLarge(override val host: String? = null) : AppError

    /** The pod is out of room for this account. */
    data class PodStorageFull(override val host: String? = null) : AppError

    /** The device ran out of room while writing a download, export, or cache entry. */
    data object DeviceStorageFull : AppError

    /** A response parsed as neither valid RDF nor the shape the app expected. */
    data class UnreadableData(
        override val host: String? = null,
        override val diagnostic: String? = null,
    ) : AppError

    /** The receiver's WebID profile advertises no `ldp:inbox`, so nothing can be delivered. */
    data class RecipientUnreachable(val recipientWebId: String? = null) : AppError

    /** The receiver's inbox exists but rejected the app's credentials or the post itself. */
    data class RecipientInboxRefused(
        val recipientWebId: String? = null,
        override val host: String? = null,
    ) : AppError

    /** The inbox post failed for a non-authorization reason. */
    data class NotificationNotDelivered(
        val recipientWebId: String? = null,
        override val host: String? = null,
    ) : AppError {
        override val retryable: Boolean get() = true
    }

    /** An inbound notification's claimed actor did not match the resource owner. */
    data object AuthenticityCheckFailed : AppError

    /** The user (or the system) cancelled the work; nothing to report. */
    data object Cancelled : AppError

    /** A local file or content URI could not be read or written. */
    data class LocalFileUnavailable(override val diagnostic: String? = null) : AppError

    /** Input the app could not make sense of — a malformed link, code, or imported file. */
    data class InvalidInput(override val diagnostic: String? = null) : AppError

    /** Nothing more specific applies. */
    data class Unexpected(override val diagnostic: String? = null) : AppError
}

/**
 * Wraps an [AppError] so a precondition the app detects itself — no active account, an
 * online-only action while offline — can travel through a throwing API and be recognised again
 * by [AppErrorMapper] rather than degrading to [AppError.Unexpected].
 */
class AppErrorException(val error: AppError) : Exception(error.diagnostic ?: error.toString())

/** Throws this error, for preconditions raised inside a `try` that already reports failures. */
fun AppError.asException(): AppErrorException = AppErrorException(this)
