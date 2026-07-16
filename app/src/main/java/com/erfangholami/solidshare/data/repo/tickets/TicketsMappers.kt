package com.erfangholami.solidshare.data.repo.tickets

import com.erfangholami.solidshare.domain.model.Ticket
import com.erfangholami.solidshare.domain.model.TicketBarcodeFormat
import com.erfangholami.solidshare.domain.model.TicketCategory
import com.erfangholami.solidshare.domain.model.TicketDraft
import com.erfangholami.solidshare.domain.model.TicketEventInfo
import com.erfangholami.solidshare.domain.model.TicketJourney
import com.erfangholami.solidshare.domain.model.TicketSeatInfo
import com.erfangholami.solidshare.domain.model.TicketSource
import com.erfangholami.solidshare.domain.model.TicketStop
import com.erfangholami.solidshare.domain.model.TicketSummaryItem
import com.erfangholami.solidshare.domain.model.TicketVenue
import com.erfangholami.solidshare.domain.model.TransportMode
import com.erfangholami.androidsolidservices.shared.model.tickets.NewTicket as LibNewTicket
import com.erfangholami.androidsolidservices.shared.model.tickets.Ticket as LibTicket
import com.erfangholami.androidsolidservices.shared.model.tickets.TicketBarcode as LibBarcode
import com.erfangholami.androidsolidservices.shared.model.tickets.TicketBarcodeFormat as LibBarcodeFormat
import com.erfangholami.androidsolidservices.shared.model.tickets.TicketCategory as LibCategory
import com.erfangholami.androidsolidservices.shared.model.tickets.TicketEvent as LibEvent
import com.erfangholami.androidsolidservices.shared.model.tickets.TicketJourney as LibJourney
import com.erfangholami.androidsolidservices.shared.model.tickets.TicketOrganization as LibOrganization
import com.erfangholami.androidsolidservices.shared.model.tickets.TicketPerson as LibPerson
import com.erfangholami.androidsolidservices.shared.model.tickets.TicketPlace as LibPlace
import com.erfangholami.androidsolidservices.shared.model.tickets.TicketSeat as LibSeat
import com.erfangholami.androidsolidservices.shared.model.tickets.TicketSource as LibSource
import com.erfangholami.androidsolidservices.shared.model.tickets.TicketStop as LibStop
import com.erfangholami.androidsolidservices.shared.model.tickets.TicketSummary as LibSummary
import com.erfangholami.androidsolidservices.shared.model.tickets.TransportMode as LibTransportMode

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
    token = primaryBarcode?.payload,
    barcodeFormat = primaryBarcode?.symbology?.toDomain() ?: TicketBarcodeFormat.NONE,
    category = category.toDomain(),
    issuer = issuer?.name ?: organizationName,
    holder = holder?.name,
    seat = seats.firstOrNull()?.let { TicketSeatInfo(it.seatNumber, it.seatRow, it.seatSection) },
    price = totalPrice,
    currency = priceCurrency,
    dateIssued = dateIssued,
    event = event?.toDomain(),
    journey = journey?.toDomain(),
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
    barcodes = token?.takeIf { it.isNotBlank() }
        ?.let { listOf(LibBarcode(payload = it, symbology = barcodeFormat.toLib())) }
        ?: emptyList(),
    category = category.toLib(),
    issuer = issuer?.takeIf { it.isNotBlank() }?.let { LibOrganization(name = it) },
    holder = holder?.takeIf { it.isNotBlank() }?.let { LibPerson(name = it) },
    seats = seat?.takeIf { it.number != null || it.row != null || it.section != null }
        ?.let { listOf(LibSeat(it.number, it.row, it.section)) }
        ?: emptyList(),
    totalPrice = price?.takeIf { it.isNotBlank() },
    priceCurrency = currency?.takeIf { it.isNotBlank() },
    dateIssued = dateIssued?.takeIf { it.isNotBlank() },
    event = event?.toLib(),
    journey = journey?.toLib(),
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
    journey = journey,
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
            ?.let { LibPlace(name = it.name, address = it.address) },
    )
}

private fun LibJourney.toDomain(): TicketJourney = TicketJourney(
    mode = runCatching { TransportMode.valueOf(mode.name) }.getOrDefault(TransportMode.FLIGHT),
    carrier = carrierName,
    serviceNumber = serviceNumber,
    from = departure?.toDomainStop(departureTime),
    to = arrival?.toDomainStop(arrivalTime),
    duration = duration,
)

private fun LibStop.toDomainStop(time: String?): TicketStop = TicketStop(
    name = name,
    code = iataCode ?: code,
    cityName = cityName,
    time = time,
    terminal = terminal,
    gate = gate,
    platform = platform,
)

private fun TicketJourney.toLib(): LibJourney {
    val air = mode == TransportMode.FLIGHT
    return LibJourney(
        mode = LibTransportMode.valueOf(mode.name),
        carrierName = carrier?.takeIf { it.isNotBlank() },
        serviceNumber = serviceNumber?.takeIf { it.isNotBlank() },
        departure = from?.toLibStop(air),
        arrival = to?.toLibStop(air),
        departureTime = from?.time?.takeIf { it.isNotBlank() },
        arrivalTime = to?.time?.takeIf { it.isNotBlank() },
        duration = duration?.takeIf { it.isNotBlank() },
    )
}

private fun TicketStop.toLibStop(air: Boolean): LibStop = LibStop(
    name = name,
    iataCode = code?.takeIf { air },
    code = code?.takeIf { !air },
    cityName = cityName,
    terminal = terminal,
    gate = gate,
    platform = platform,
)

fun LibCategory.toDomain(): TicketCategory =
    runCatching { TicketCategory.valueOf(name) }.getOrDefault(TicketCategory.GENERIC)

fun TicketCategory.toLib(): LibCategory = LibCategory.valueOf(name)

fun LibBarcodeFormat.toDomain(): TicketBarcodeFormat =
    runCatching { TicketBarcodeFormat.valueOf(name) }.getOrDefault(TicketBarcodeFormat.NONE)

fun TicketBarcodeFormat.toLib(): LibBarcodeFormat = LibBarcodeFormat.valueOf(name)

fun LibSource.toDomain(): TicketSource =
    runCatching { TicketSource.valueOf(name) }.getOrDefault(TicketSource.MANUAL)

fun TicketSource.toLib(): LibSource = LibSource.valueOf(name)
