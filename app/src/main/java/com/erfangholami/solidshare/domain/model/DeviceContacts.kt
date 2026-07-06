package com.erfangholami.solidshare.domain.model

data class DeviceContactRef(
    val rawContactId: Long,
    val name: String,
)

data class DeviceAccount(
    val accountType: String?,
    val accountName: String?,
    val contacts: List<DeviceContactRef>,
)

data class DeviceContactData(
    val draft: ContactDraft,
    val photo: ByteArray? = null,
    val photoMime: String? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DeviceContactData) return false
        return draft == other.draft &&
                photoMime == other.photoMime &&
                (photo?.contentEquals(other.photo ?: return false) ?: (other.photo == null))
    }

    override fun hashCode(): Int {
        var result = draft.hashCode()
        result = 31 * result + (photo?.contentHashCode() ?: 0)
        result = 31 * result + (photoMime?.hashCode() ?: 0)
        return result
    }
}
