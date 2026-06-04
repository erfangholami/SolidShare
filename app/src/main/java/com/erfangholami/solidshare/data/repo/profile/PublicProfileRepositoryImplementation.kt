package com.erfangholami.solidshare.data.repo.profile

import com.erfangholami.androidsolidservices.api.resource.SolidResourceManager
import com.erfangholami.androidsolidservices.shared.http.SolidNetworkResponse
import com.erfangholami.androidsolidservices.shared.model.profile.Profile
import com.erfangholami.androidsolidservices.shared.model.profile.WebId
import com.erfangholami.androidsolidservices.shared.rdf.patch.N3Patch
import com.erfangholami.androidsolidservices.shared.vocab.FOAF
import com.erfangholami.androidsolidservices.shared.vocab.VCARD
import com.erfangholami.solidshare.domain.model.ProfileEdits
import com.erfangholami.solidshare.domain.model.PublicProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URI
import javax.inject.Inject

private const val MAX_EXTENDED_PROFILES = 3

class PublicProfileRepositoryImplementation @Inject constructor(
    private val resourceManager: SolidResourceManager,
) : PublicProfileRepository {

    override suspend fun fetchByWebId(webId: String): Result<PublicProfile> = runCatching {
        val docUrl = webId.substringBefore("#")
        val primary = readPublicWebId(docUrl)
        val realWebId = primary.findProperty(FOAF.PRIMARY_TOPIC)?.takeIf { it.isReadable() }
            ?: webId.takeIf { it.contains("#") }
            ?: docUrl
        var profile = mapWebId(realWebId, docUrl, primary)
        if (profile.lacksName()) {
            val extended = (primary.getRelatedResources() + primary.getPrimaryTopicDocuments())
                .map { it.toString() }
                .filter { it.substringBefore("#") != docUrl }
                .distinct()
                .take(MAX_EXTENDED_PROFILES)
            for (uri in extended) {
                if (!profile.lacksName()) break
                val ext = runCatching { readPublicWebId(uri) }.getOrNull() ?: continue
                profile = profile.mergeFrom(ext)
            }
        }
        profile
    }

    override fun fromProfile(profile: Profile): PublicProfile? {
        val webIdString = profile.userInfo?.webId ?: return null
        val webId = profile.webId ?: return PublicProfile(
            webId = webIdString,
            profileDocumentUrl = webIdString.substringBefore("#"),
            name = null,
            givenName = null,
            familyName = null,
            photoUri = null,
            emails = emptyList(),
            phones = emptyList(),
            organization = null,
            role = null,
            oidcIssuer = null,
        )
        return mapWebId(webIdString, webIdString.substringBefore("#"), webId)
    }

    override suspend fun updateProfile(
        webId: String,
        edits: ProfileEdits,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val docUri = URI.create(webId.substringBefore("#"))
            val original = when (val r = resourceManager.read(webId, docUri, WebId::class.java)) {
                is SolidNetworkResponse.Success -> r.data
                is SolidNetworkResponse.Error -> error("HTTP ${r.errorCode}: ${r.errorMessage}")
                is SolidNetworkResponse.Exception -> throw r.exception
            }
            val updated = WebId(original.getIdentifier(), original.getAllQuads()).apply {
                setLiteral(webId, FOAF.NAME, edits.name)
                setLiteral(webId, VCARD.FN, edits.name)
                setLiteral(webId, FOAF.GIVEN_NAME, edits.givenName)
                setLiteral(webId, VCARD.GIVEN_NAME, edits.givenName)
                setLiteral(webId, FOAF.FAMILY_NAME, edits.familyName)
                setLiteral(webId, VCARD.FAMILY_NAME, edits.familyName)
                setLiteral(webId, VCARD.ROLE, edits.role)
                setLiteral(webId, VCARD.ORGANIZATION_NAME, edits.organization)
            }
            val patch = runCatching { N3Patch.fromDiff(original, updated) }.getOrNull()
                ?: return@runCatching
            when (val r = resourceManager.patch(webId, docUri, patch, ifMatch = null)) {
                is SolidNetworkResponse.Success -> Unit
                is SolidNetworkResponse.Error -> error("HTTP ${r.errorCode}: ${r.errorMessage}")
                is SolidNetworkResponse.Exception -> throw r.exception
            }
        }
    }

    private fun WebId.setLiteral(subject: String, predicate: String, value: String) {
        clearProperties(predicate, subject)
        val trimmed = value.trim()
        if (trimmed.isNotEmpty()) addQuadLiteral(subject, predicate, trimmed)
    }

    private suspend fun readPublicWebId(url: String): WebId =
        when (val r = resourceManager.readPublic(URI.create(url), WebId::class.java)) {
            is SolidNetworkResponse.Success -> r.data
            is SolidNetworkResponse.Error -> error("HTTP ${r.errorCode}: ${r.errorMessage}")
            is SolidNetworkResponse.Exception -> throw r.exception
        }

    private fun mapWebId(displayWebId: String, docUrl: String, webId: WebId): PublicProfile =
        PublicProfile(
            webId = displayWebId,
            profileDocumentUrl = docUrl,
            name = webId.getName()?.takeIf { it.isNotBlank() },
            givenName = webId.getGivenName()?.takeIf { it.isNotBlank() },
            familyName = webId.getFamilyName()?.takeIf { it.isNotBlank() },
            photoUri = webId.getPhoto()?.toString(),
            emails = webId.findAllProperties(VCARD.HAS_EMAIL).filter { it.isReadable() },
            phones = webId.findAllProperties(VCARD.HAS_TELEPHONE).filter { it.isReadable() },
            organization = webId.findProperty(VCARD.ORGANIZATION_NAME)?.takeIf { it.isReadable() },
            role = webId.findProperty(VCARD.ROLE)?.takeIf { it.isReadable() },
            oidcIssuer = webId.getOidcIssuers().firstOrNull()?.toString(),
        )

    private fun PublicProfile.mergeFrom(ext: WebId): PublicProfile = copy(
        name = name ?: ext.getName()?.takeIf { it.isNotBlank() },
        givenName = givenName ?: ext.getGivenName()?.takeIf { it.isNotBlank() },
        familyName = familyName ?: ext.getFamilyName()?.takeIf { it.isNotBlank() },
        photoUri = photoUri ?: ext.getPhoto()?.toString(),
        emails = emails.ifEmpty {
            ext.findAllProperties(VCARD.HAS_EMAIL).filter { it.isReadable() }
        },
        phones = phones.ifEmpty {
            ext.findAllProperties(VCARD.HAS_TELEPHONE).filter { it.isReadable() }
        },
        organization = organization ?: ext.findProperty(VCARD.ORGANIZATION_NAME)
            ?.takeIf { it.isReadable() },
        role = role ?: ext.findProperty(VCARD.ROLE)?.takeIf { it.isReadable() },
        oidcIssuer = oidcIssuer ?: ext.getOidcIssuers().firstOrNull()?.toString(),
    )

    private fun PublicProfile.lacksName(): Boolean =
        name.isNullOrBlank() && givenName.isNullOrBlank() && familyName.isNullOrBlank()

    private fun String.isReadable(): Boolean =
        isNotBlank() && !startsWith("_:")
}
