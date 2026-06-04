package com.erfangholami.solidshare.presentation.sharing

import com.erfangholami.solidshare.R
import com.erfangholami.solidshare.data.repo.sharing.SharingError
import com.erfangholami.solidshare.data.repo.sharing.toSharingError
import com.erfangholami.solidshare.util.StringProvider

fun Throwable.toSharingErrorMessage(strings: StringProvider): String = when (val error = toSharingError()) {
    is SharingError.AccessDenied ->
        error.ownerWebId?.let {
            strings.getString(R.string.error_access_denied_with_owner, it)
        } ?: strings.getString(R.string.error_access_denied_no_owner)

    is SharingError.StaleAcl ->
        strings.getString(R.string.error_stale_acl)

    is SharingError.AccessIndeterminate ->
        strings.getString(R.string.error_access_indeterminate)

    is SharingError.IncompleteScan ->
        strings.getString(R.string.error_incomplete_scan)

    is SharingError.NoInbox ->
        strings.getString(R.string.error_no_inbox)

    is SharingError.InboxUnauthorized ->
        strings.getString(R.string.error_inbox_unauthorized)

    is SharingError.InboxForbidden ->
        strings.getString(R.string.error_inbox_forbidden)

    is SharingError.NotificationDelivery ->
        strings.getString(R.string.error_notification_delivery)

    is SharingError.ImpersonationDetected ->
        strings.getString(R.string.error_impersonation)

    is SharingError.UnsupportedAuthBackend ->
        strings.getString(R.string.error_unsupported_auth)

    is SharingError.Unknown ->
        error.message ?: strings.getString(R.string.error_something_went_wrong)
}
