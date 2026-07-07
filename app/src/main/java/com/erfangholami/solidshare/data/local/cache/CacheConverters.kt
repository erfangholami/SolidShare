package com.erfangholami.solidshare.data.local.cache

import androidx.room.TypeConverter
import com.erfangholami.solidshare.domain.model.ResourceAccess
import com.erfangholami.solidshare.domain.model.ResourceType
import kotlinx.serialization.json.Json

class CacheConverters {

    @TypeConverter
    fun stringListToJson(value: List<String>): String = json.encodeToString(value)

    @TypeConverter
    fun jsonToStringList(value: String): List<String> = json.decodeFromString(value)

    @TypeConverter
    fun accessToJson(value: ResourceAccess): String = json.encodeToString(value)

    @TypeConverter
    fun jsonToAccess(value: String): ResourceAccess = json.decodeFromString(value)

    @TypeConverter
    fun resourceTypeToName(value: ResourceType): String = value.name

    @TypeConverter
    fun nameToResourceType(value: String): ResourceType = ResourceType.valueOf(value)

    @TypeConverter
    fun syncStateToName(value: SyncState): String = value.name

    @TypeConverter
    fun nameToSyncState(value: String): SyncState = SyncState.valueOf(value)

    @TypeConverter
    fun blobStateToName(value: BlobState): String = value.name

    @TypeConverter
    fun nameToBlobState(value: String): BlobState = BlobState.valueOf(value)

    @TypeConverter
    fun opTypeToName(value: OpType): String = value.name

    @TypeConverter
    fun nameToOpType(value: String): OpType = OpType.valueOf(value)

    @TypeConverter
    fun opStatusToName(value: OpStatus): String = value.name

    @TypeConverter
    fun nameToOpStatus(value: String): OpStatus = OpStatus.valueOf(value)

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
    }
}
