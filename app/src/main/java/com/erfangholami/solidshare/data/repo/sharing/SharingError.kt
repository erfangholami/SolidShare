package com.erfangholami.solidshare.data.repo.sharing

import com.erfangholami.androidsolidservices.api.exceptions.SharingException

/**
 * App-domain failures raised by the sharing layer, mapped from the library's
 * [SharingException] at the repository boundary so the presentation layer never
 * depends on the underlying Solid client types.
 */
sealed class SharingError(message: String) : Exception(message) {
    class AccessDenied(val resourceUri: String, val ownerWebId: String?) :
        SharingError("No access to $resourceUri")

    class StaleAcl : SharingError("The share was changed elsewhere")
    class AccessIndeterminate : SharingError("Access could not be verified")
    class IncompleteScan : SharingError("The pod could not be fully scanned")
    class NoInbox : SharingError("The recipient advertises no inbox")
    class InboxUnauthorized : SharingError("Not authorized to notify the recipient")
    class InboxForbidden : SharingError("The recipient's inbox refused the notification")
    class NotificationDelivery : SharingError("The notification could not be delivered")
    class ImpersonationDetected : SharingError("The notification failed an authenticity check")
    class UnsupportedAuthBackend :
        SharingError("The server's access-control backend is unsupported")

    class Unknown(message: String) : SharingError(message)
}

fun Throwable.toSharingError(): SharingError = when (this) {
    is SharingError -> this
    is SharingException.AccessDenied -> SharingError.AccessDenied(resourceUri, ownerWebId)
    is SharingException.StaleAcl -> SharingError.StaleAcl()
    is SharingException.AccessIndeterminate -> SharingError.AccessIndeterminate()
    is SharingException.IncompleteScan -> SharingError.IncompleteScan()
    is SharingException.NoInbox -> SharingError.NoInbox()
    is SharingException.InboxUnauthorized -> SharingError.InboxUnauthorized()
    is SharingException.InboxForbidden -> SharingError.InboxForbidden()
    is SharingException.NotificationDelivery -> SharingError.NotificationDelivery()
    is SharingException.ImpersonationDetected -> SharingError.ImpersonationDetected()
    is SharingException.UnsupportedAuthBackend -> SharingError.UnsupportedAuthBackend()
    else -> SharingError.Unknown(message ?: "Something went wrong.")
}
