package com.erfangholami.solidshare.domain.model

/**
 * Lightweight metadata for a single resource, fetched on demand (e.g. for a ⋮ sheet header of a
 * shared resource where only the URL is known). [sizeBytes] for files, [itemCount] for containers;
 * [lastModified] is epoch millis. Any field may be null when the server doesn't report it.
 */
data class ResourceMeta(
    val sizeBytes: Long?,
    val lastModified: Long?,
    val itemCount: Int?,
)
