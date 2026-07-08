package com.erfangholami.solidshare.data.local.cache

import com.erfangholami.solidshare.domain.model.ResourceAccess
import org.junit.Assert.assertEquals
import org.junit.Test

class CacheConvertersTest {

    private val converters = CacheConverters()

    @Test
    fun stringList_roundTrips() {
        val value = listOf("a", "b/c", "http://example.org/x#me")
        assertEquals(value, converters.jsonToStringList(converters.stringListToJson(value)))
    }

    @Test
    fun emptyStringList_roundTrips() {
        assertEquals(emptyList<String>(), converters.jsonToStringList(converters.stringListToJson(emptyList())))
    }

    @Test
    fun resourceAccess_roundTrips() {
        val value = ResourceAccess(
            canWrite = true,
            canControl = false,
            publicCanRead = true,
            canAppend = false,
        )
        assertEquals(value, converters.jsonToAccess(converters.accessToJson(value)))
    }

    @Test
    fun enums_roundTrip() {
        assertEquals(
            SyncState.PENDING_DELETE,
            converters.nameToSyncState(converters.syncStateToName(SyncState.PENDING_DELETE)),
        )
        assertEquals(
            BlobState.PENDING_UPLOAD,
            converters.nameToBlobState(converters.blobStateToName(BlobState.PENDING_UPLOAD)),
        )
        assertEquals(OpType.COPY, converters.nameToOpType(converters.opTypeToName(OpType.COPY)))
        assertEquals(OpStatus.FAILED, converters.nameToOpStatus(converters.opStatusToName(OpStatus.FAILED)))
    }
}
