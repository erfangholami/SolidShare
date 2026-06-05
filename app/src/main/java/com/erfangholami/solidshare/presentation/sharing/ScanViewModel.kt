package com.erfangholami.solidshare.presentation.sharing

import androidx.lifecycle.ViewModel
import com.erfangholami.solidshare.data.repo.sharing.SharingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class ScanViewModel @Inject constructor(
    private val sharingRepository: SharingRepository,
) : ViewModel() {

    sealed interface ScanTarget {
        data class Share(val resourceUri: String, val ownerWebId: String?) : ScanTarget
        data class Profile(val webId: String) : ScanTarget
        data object Unrecognized : ScanTarget
    }

    fun classify(raw: String): ScanTarget {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return ScanTarget.Unrecognized
        val parsed = sharingRepository.parseDeepLink(trimmed)
        return when {
            parsed != null -> ScanTarget.Share(parsed.resourceUri, parsed.ownerWebId)
            looksLikeWebId(trimmed) -> ScanTarget.Profile(trimmed)
            else -> ScanTarget.Unrecognized
        }
    }
}

internal fun looksLikeWebId(raw: String): Boolean {
    val trimmed = raw.trim()
    return trimmed.startsWith("http://", ignoreCase = true)
            || trimmed.startsWith("https://", ignoreCase = true)
}
