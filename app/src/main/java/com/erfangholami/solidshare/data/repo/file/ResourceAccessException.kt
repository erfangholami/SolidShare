package com.erfangholami.solidshare.data.repo.file

/** Typed access failures from resource reads, raised on HTTP 401/403. */
sealed class ResourceAccessException(message: String) : Exception(message) {
    class AccessDenied(val resourceUri: String) :
        ResourceAccessException("No access to $resourceUri")
}
