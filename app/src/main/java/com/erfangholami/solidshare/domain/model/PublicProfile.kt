package com.erfangholami.solidshare.domain.model

/**
 * Plain-data view of a Solid WebID profile used by the UI layer, covering both
 * the active logged-in account and externally fetched profiles.
 */
data class PublicProfile(
    val webId: String,
    val profileDocumentUrl: String,
    val name: String?,
    val givenName: String?,
    val familyName: String?,
    val photoUri: String?,
    val emails: List<String>,
    val phones: List<String>,
    val organization: String?,
    val role: String?,
    val oidcIssuer: String?,
) {
    val displayName: String
        get() = name?.takeIf { it.isNotBlank() }
            ?: listOfNotNull(givenName, familyName).joinToString(" ").trim()
                .takeIf { it.isNotEmpty() }
            ?: webId
}

data class ProfileEdits(
    val name: String,
    val givenName: String,
    val familyName: String,
    val role: String,
    val organization: String,
)
