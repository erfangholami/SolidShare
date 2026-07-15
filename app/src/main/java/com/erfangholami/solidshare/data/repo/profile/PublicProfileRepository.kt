package com.erfangholami.solidshare.data.repo.profile

import com.erfangholami.androidsolidservices.shared.model.profile.SolidAccount
import com.erfangholami.solidshare.domain.model.ProfileEdits
import com.erfangholami.solidshare.domain.model.PublicProfile

interface PublicProfileRepository {

    suspend fun fetchByWebId(webId: String): Result<PublicProfile>

    fun fromProfile(profile: SolidAccount): PublicProfile?

    suspend fun updateProfile(webId: String, edits: ProfileEdits): Result<Unit>
}
