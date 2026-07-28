package com.erfangholami.solidshare.data.local.cache

import androidx.room.Entity
import androidx.room.Index
import com.erfangholami.solidshare.domain.model.Ticket
import com.erfangholami.solidshare.domain.model.TicketCategory
import com.erfangholami.solidshare.domain.model.TicketSummaryItem
import kotlinx.serialization.json.Json

@Entity(
    tableName = "cached_ticket",
    primaryKeys = ["webId", "ticketUri"],
    indices = [Index(value = ["webId"])],
)
data class CachedTicketEntity(
    val webId: String,
    val ticketUri: String,
    val title: String,
    val category: String,
    val eventStart: String? = null,
    val issuer: String? = null,
    val validThrough: String? = null,
    val backgroundColor: String? = null,
    val foregroundColor: String? = null,
    val detailJson: String,
    val etag: String? = null,
    val syncState: SyncState = SyncState.SYNCED,
    val cachedAt: Long,
)

private val ticketJson = Json { ignoreUnknownKeys = true }

fun CachedTicketEntity.toDomain(): Ticket =
    ticketJson.decodeFromString(Ticket.serializer(), detailJson)

fun CachedTicketEntity.toSummary(): TicketSummaryItem = TicketSummaryItem(
    uri = ticketUri,
    title = title,
    category = runCatching { TicketCategory.valueOf(category) }
        .getOrDefault(TicketCategory.GENERIC),
    eventStart = eventStart,
    issuer = issuer,
    validThrough = validThrough,
    backgroundColor = backgroundColor,
    foregroundColor = foregroundColor,
    pending = syncState != SyncState.SYNCED,
)

fun Ticket.toCacheEntity(
    webId: String,
    cachedAt: Long,
    etag: String? = null,
    syncState: SyncState = SyncState.SYNCED,
): CachedTicketEntity = CachedTicketEntity(
    webId = webId,
    ticketUri = uri,
    title = title,
    category = category.name,
    eventStart = event?.start ?: journey?.from?.time,
    issuer = issuer,
    validThrough = validThrough,
    backgroundColor = style?.backgroundColor,
    foregroundColor = style?.foregroundColor,
    detailJson = ticketJson.encodeToString(Ticket.serializer(), this),
    etag = etag,
    syncState = syncState,
    cachedAt = cachedAt,
)
