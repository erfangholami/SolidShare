package com.erfangholami.solidshare.data.repo.tickets

import com.erfangholami.solidshare.domain.model.Ticket
import com.erfangholami.solidshare.domain.model.TicketBarcodeFormat
import com.erfangholami.solidshare.domain.model.TicketCategory
import com.erfangholami.solidshare.domain.model.TicketDraft
import com.erfangholami.solidshare.domain.model.TicketEventInfo
import com.erfangholami.solidshare.domain.model.TicketSeatInfo
import com.erfangholami.solidshare.domain.model.TicketSource
import com.erfangholami.solidshare.domain.model.TicketSummaryItem
import com.erfangholami.solidshare.domain.model.TicketVenue
import com.erfangholami.androidsolidservices.shared.model.tickets.NewTicket as LibNewTicket
import com.erfangholami.androidsolidservices.shared.model.tickets.Ticket as LibTicket
import com.erfangholami.androidsolidservices.shared.model.tickets.TicketBarcodeFormat as LibBarcodeFormat
import com.erfangholami.androidsolidservices.shared.model.tickets.TicketCategory as LibCategory
import com.erfangholami.androidsolidservices.shared.model.tickets.TicketEvent as LibEvent
import com.erfangholami.androidsolidservices.shared.model.tickets.TicketPlace as LibPlace
import com.erfangholami.androidsolidservices.shared.model.tickets.TicketSeat as LibSeat
import com.erfangholami.androidsolidservices.shared.model.tickets.TicketSource as LibSource
import com.erfangholami.androidsolidservices.shared.model.tickets.TicketSummary as LibSummary

fun LibSummary.toDomain(): TicketSummaryItem = TicketSummaryItem(
    uri = uri,
    title = title,
    category = category.toDomain(),
    eventStart = eventStart,
    issuer = issuer,
    validThrough = validThrough,
)

fun LibTicket.toDomain(): Ticket = Ticket(
    uri = uri,
    title = title,
    description = description,
    number = ticketNumber,
    token = ticketToken,
    barcodeFormat = barcodeFormat.toDomain(),
    category = category.toDomain(),
    issuer = issuerName,
    holder = underName,
    seat = seat?.let { TicketSeatInfo(it.seatNumber, it.seatRow, it.seatSection) },
    price = totalPrice,
    currency = priceCurrency,
    dateIssued = dateIssued,
    event = event?.toDomain(),
    validFrom = validFrom,
    validThrough = validThrough,
    source = source.toDomain(),
    artifactUri = artifactUri,
    createdAt = createdAt,
    modifiedAt = modifiedAt,
)

fun TicketDraft.toLib(): LibNewTicket = LibNewTicket(
    title = title.trim(),
    description = description?.takeIf { it.isNotBlank() },
    ticketNumber = number?.takeIf { it.isNotBlank() },
    ticketToken = token?.takeIf { it.isNotBlank() },
    barcodeFormat = barcodeFormat.toLib(),
    category = category.toLib(),
    issuerName = issuer?.takeIf { it.isNotBlank() },
    underName = holder?.takeIf { it.isNotBlank() },
    seat = seat?.takeIf { it.number != null || it.row != null || it.section != null }
        ?.let { LibSeat(it.number, it.row, it.section) },
    totalPrice = price?.takeIf { it.isNotBlank() },
    priceCurrency = currency?.takeIf { it.isNotBlank() },
    dateIssued = dateIssued?.takeIf { it.isNotBlank() },
    event = event?.toLib(),
    validFrom = validFrom?.takeIf { it.isNotBlank() },
    validThrough = validThrough?.takeIf { it.isNotBlank() },
    source = source.toLib(),
)

fun Ticket.toDraft(): TicketDraft = TicketDraft(
    title = title,
    description = description,
    number = number,
    token = token,
    barcodeFormat = barcodeFormat,
    category = category,
    issuer = issuer,
    holder = holder,
    seat = seat,
    price = price,
    currency = currency,
    dateIssued = dateIssued,
    event = event,
    validFrom = validFrom,
    validThrough = validThrough,
    source = source,
)

private fun LibEvent.toDomain(): TicketEventInfo = TicketEventInfo(
    name = name,
    start = startDate,
    end = endDate,
    venue = location?.let { TicketVenue(it.name, it.address) },
)

private fun TicketEventInfo.toLib(): LibEvent? {
    val hasContent = name != null || start != null || end != null || venue != null
    if (!hasContent) return null
    return LibEvent(
        name = name?.takeIf { it.isNotBlank() },
        startDate = start?.takeIf { it.isNotBlank() },
        endDate = end?.takeIf { it.isNotBlank() },
        location = venue?.takeIf { it.name != null || it.address != null }
            ?.let { LibPlace(it.name, it.address) },
    )
}

fun LibCategory.toDomain(): TicketCategory = TicketCategory.valueOf(name)
fun TicketCategory.toLib(): LibCategory = LibCategory.valueOf(name)
fun LibBarcodeFormat.toDomain(): TicketBarcodeFormat = TicketBarcodeFormat.valueOf(name)
fun TicketBarcodeFormat.toLib(): LibBarcodeFormat = LibBarcodeFormat.valueOf(name)
fun LibSource.toDomain(): TicketSource = TicketSource.valueOf(name)
fun TicketSource.toLib(): LibSource = LibSource.valueOf(name)
