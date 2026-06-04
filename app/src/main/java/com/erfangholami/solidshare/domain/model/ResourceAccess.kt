package com.erfangholami.solidshare.domain.model

import com.erfangholami.solidshare.domain.model.ResourceAccess.Companion.FULL
import kotlinx.serialization.Serializable

/**
 * The current user's effective access on a Solid resource, distilled from the
 * server's `WAC-Allow` response header. Gates UI actions when browsing a shared
 * container where rights may be less than [FULL].
 */
@Serializable
data class ResourceAccess(
    val canWrite: Boolean,
    val canControl: Boolean,
    val publicCanRead: Boolean,
    val canAppend: Boolean,
) {
    val canModify: Boolean get() = canWrite

    val canAddTo: Boolean get() = canAppend || canWrite

    val canShareOnward: Boolean get() = canControl || publicCanRead

    companion object {
        val FULL = ResourceAccess(
            canWrite = true,
            canControl = true,
            publicCanRead = false,
            canAppend = true,
        )

        val READ_ONLY = ResourceAccess(
            canWrite = false,
            canControl = false,
            publicCanRead = false,
            canAppend = false,
        )
    }
}
