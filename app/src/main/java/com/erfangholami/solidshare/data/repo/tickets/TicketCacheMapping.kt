package com.erfangholami.solidshare.data.repo.tickets

import com.erfangholami.solidshare.data.local.cache.CachedEntityEntity
import com.erfangholami.solidshare.data.local.cache.SyncState
import com.erfangholami.solidshare.data.repo.datamodule.DataModuleIds
import com.erfangholami.solidshare.domain.model.Ticket
import com.erfangholami.solidshare.domain.model.TicketSummaryItem
import kotlinx.serialization.json.Json

private val ticketJson = Json { ignoreUnknownKeys = true }

fun Ticket.toCacheEntity(
    webId: String,
    cachedAt: Long,
    etag: String? = null,
    syncState: SyncState = SyncState.SYNCED,
): CachedEntityEntity = CachedEntityEntity(
    module = DataModuleIds.TICKETS,
    webId = webId,
    uri = uri,
    sortKey = event?.start ?: journey?.from?.time,
    groupKey = category.name,
    searchText = title.lowercase(),
    detailJson = ticketJson.encodeToString(Ticket.serializer(), this),
    etag = etag,
    syncState = syncState,
    cachedAt = cachedAt,
)

fun CachedEntityEntity.toTicket(): Ticket =
    ticketJson.decodeFromString(Ticket.serializer(), detailJson)

fun CachedEntityEntity.toTicketSummary(): TicketSummaryItem {
    val ticket = toTicket()
    return TicketSummaryItem(
        uri = uri,
        title = ticket.title,
        category = ticket.category,
        eventStart = ticket.event?.start ?: ticket.journey?.from?.time,
        issuer = ticket.issuer,
        validThrough = ticket.validThrough,
        backgroundColor = ticket.style?.backgroundColor,
        foregroundColor = ticket.style?.foregroundColor,
        pending = syncState != SyncState.SYNCED,
    )
}
