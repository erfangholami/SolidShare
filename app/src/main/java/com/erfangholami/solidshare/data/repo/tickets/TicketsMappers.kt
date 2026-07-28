package com.erfangholami.solidshare.data.repo.tickets

import com.erfangholami.solidshare.domain.model.Ticket
import com.erfangholami.solidshare.domain.model.TicketBarcodeFormat
import com.erfangholami.solidshare.domain.model.TicketCategory
import com.erfangholami.solidshare.domain.model.TicketDraft
import com.erfangholami.solidshare.domain.model.TicketEventInfo
import com.erfangholami.solidshare.domain.model.TicketExtra
import com.erfangholami.solidshare.domain.model.TicketExtraPlacement
import com.erfangholami.solidshare.domain.model.TicketBeaconInfo
import com.erfangholami.solidshare.domain.model.TicketImageUris
import com.erfangholami.solidshare.domain.model.TicketJourney
import com.erfangholami.solidshare.domain.model.TicketLocationInfo
import com.erfangholami.solidshare.domain.model.TicketMembershipInfo
import com.erfangholami.solidshare.domain.model.TicketPassInfo
import com.erfangholami.solidshare.domain.model.TicketReservationInfo
import com.erfangholami.solidshare.domain.model.TicketSeatInfo
import com.erfangholami.solidshare.domain.model.TicketWifiInfo
import com.erfangholami.solidshare.domain.model.TicketSource
import com.erfangholami.solidshare.domain.model.TicketStop
import com.erfangholami.solidshare.domain.model.TicketStyle
import com.erfangholami.solidshare.domain.model.TicketSummaryItem
import com.erfangholami.solidshare.domain.model.TicketVenue
import com.erfangholami.solidshare.domain.model.TransportMode
import com.erfangholami.androidsolidservices.shared.model.tickets.NewTicket as LibNewTicket
import com.erfangholami.androidsolidservices.shared.model.tickets.Ticket as LibTicket
import com.erfangholami.androidsolidservices.shared.model.tickets.TicketBarcode as LibBarcode
import com.erfangholami.androidsolidservices.shared.model.tickets.TicketBeacon as LibBeacon
import com.erfangholami.androidsolidservices.shared.model.tickets.TicketBarcodeFormat as LibBarcodeFormat
import com.erfangholami.androidsolidservices.shared.model.tickets.DetailPlacement as LibPlacement
import com.erfangholami.androidsolidservices.shared.model.tickets.TicketCategory as LibCategory
import com.erfangholami.androidsolidservices.shared.model.tickets.TicketDetail as LibDetail
import com.erfangholami.androidsolidservices.shared.model.tickets.TicketEvent as LibEvent
import com.erfangholami.androidsolidservices.shared.model.tickets.TicketGeo as LibGeo
import com.erfangholami.androidsolidservices.shared.model.tickets.TicketJourney as LibJourney
import com.erfangholami.androidsolidservices.shared.model.tickets.TicketMembership as LibMembership
import com.erfangholami.androidsolidservices.shared.model.tickets.TicketOrganization as LibOrganization
import com.erfangholami.androidsolidservices.shared.model.tickets.TicketRelevantLocation as LibRelevantLocation
import com.erfangholami.androidsolidservices.shared.model.tickets.TicketReservation as LibReservation
import com.erfangholami.androidsolidservices.shared.model.tickets.TicketPerson as LibPerson
import com.erfangholami.androidsolidservices.shared.model.tickets.TicketPlace as LibPlace
import com.erfangholami.androidsolidservices.shared.model.tickets.TicketSeat as LibSeat
import com.erfangholami.androidsolidservices.shared.model.tickets.TicketSource as LibSource
import com.erfangholami.androidsolidservices.shared.model.tickets.TicketStop as LibStop
import com.erfangholami.androidsolidservices.shared.model.tickets.TicketStyle as LibStyle
import com.erfangholami.androidsolidservices.shared.model.tickets.TicketSummary as LibSummary
import com.erfangholami.androidsolidservices.shared.model.tickets.TicketWifi as LibWifi
import com.erfangholami.androidsolidservices.shared.model.tickets.TransportMode as LibTransportMode

fun LibSummary.toDomain(): TicketSummaryItem = TicketSummaryItem(
    uri = uri,
    title = title,
    category = category.toDomain(),
    eventStart = eventStart,
    issuer = issuer,
    validThrough = validThrough,
    backgroundColor = backgroundColor,
    foregroundColor = foregroundColor,
)

fun LibTicket.toDomain(): Ticket = Ticket(
    uri = uri,
    title = title,
    description = description,
    number = ticketNumber,
    token = primaryBarcode?.payload,
    barcodeFormat = primaryBarcode?.symbology?.toDomain() ?: TicketBarcodeFormat.NONE,
    barcodeEncoding = primaryBarcode?.encoding,
    barcodeAltText = primaryBarcode?.altText,
    category = category.toDomain(),
    issuer = issuer?.name ?: organizationName,
    holder = holder?.name,
    seat = seats.firstOrNull()?.let {
        TicketSeatInfo(
            number = it.seatNumber,
            row = it.seatRow,
            section = it.seatSection,
            type = it.seatingType,
            identifier = it.seatIdentifier,
            description = it.seatDescription,
        )
    },
    price = totalPrice,
    currency = priceCurrency,
    dateIssued = dateIssued,
    event = event?.toDomain(),
    journey = journey?.toDomain(),
    validFrom = validFrom,
    validThrough = validThrough,
    style = style?.toDomain(),
    extras = details.sortedBy { it.order ?: Int.MAX_VALUE }.mapNotNull { it.toDomain() },
    passInfo = TicketPassInfo(
        passTypeIdentifier = passTypeIdentifier,
        serialNumber = serialNumber,
        teamIdentifier = teamIdentifier,
        webServiceUrl = webServiceUrl,
        authenticationToken = authenticationToken,
        groupingIdentifier = groupingIdentifier,
        sharingProhibited = sharingProhibited,
    ).takeIf { !it.isEmpty },
    reservation = reservation?.toDomain(),
    membership = membership?.toDomain(),
    locations = relevantLocations.map {
        TicketLocationInfo(
            latitude = it.geo?.latitude,
            longitude = it.geo?.longitude,
            altitude = it.geo?.elevation,
            maxDistance = it.maxDistance,
            relevantText = it.relevantText,
        )
    },
    beacons = beacons.map { TicketBeaconInfo(it.proximityUuid, it.major, it.minor, it.relevantText) },
    wifi = wifiNetworks.map { TicketWifiInfo(it.ssid, it.password) },
    voided = voided,
    silenceRequested = silenceRequested,
    relevantStart = relevantStartDate ?: relevantDate,
    relevantEnd = relevantEndDate,
    source = source.toDomain(),
    artifactUri = artifactUri,
    images = images?.let {
        TicketImageUris(
            logo = it.logo,
            icon = it.icon,
            strip = it.strip,
            thumbnail = it.thumbnail,
            footer = it.footer,
            background = it.background,
        )
    },
    createdAt = createdAt,
    modifiedAt = modifiedAt,
)

private fun LibReservation.toDomain(): TicketReservationInfo? = TicketReservationInfo(
    boardingGroup = boardingGroup,
    boardingZone = boardingZone,
    sequenceNumber = passengerSequenceNumber,
    fareClass = fareClass,
    priorityStatus = passengerPriorityStatus,
    securityScreening = securityScreening,
).takeIf { !it.isEmpty }

private fun LibMembership.toDomain(): TicketMembershipInfo? = TicketMembershipInfo(
    programName = programName,
    number = membershipNumber,
    status = membershipStatus,
    pointsBalance = pointsBalance,
    balance = balance,
    balanceCurrency = balanceCurrency,
).takeIf { !it.isEmpty }

fun TicketDraft.toLib(): LibNewTicket = LibNewTicket(
    title = title.trim(),
    description = description?.takeIf { it.isNotBlank() },
    ticketNumber = number?.takeIf { it.isNotBlank() },
    barcodes = token?.takeIf { it.isNotBlank() }
        ?.let {
            listOf(
                LibBarcode(
                    payload = it,
                    symbology = barcodeFormat.toLib(),
                    encoding = barcodeEncoding?.takeIf { enc -> enc.isNotBlank() },
                    altText = barcodeAltText?.takeIf { alt -> alt.isNotBlank() },
                ),
            )
        }
        ?: emptyList(),
    category = category.toLib(),
    issuer = issuer?.takeIf { it.isNotBlank() }?.let { LibOrganization(name = it) },
    holder = holder?.takeIf { it.isNotBlank() }?.let { LibPerson(name = it) },
    seats = seat?.takeIf {
        it.number != null || it.row != null || it.section != null ||
            it.type != null || it.identifier != null || it.description != null
    }
        ?.let {
            listOf(
                LibSeat(
                    seatNumber = it.number,
                    seatRow = it.row,
                    seatSection = it.section,
                    seatingType = it.type,
                    seatIdentifier = it.identifier,
                    seatDescription = it.description,
                ),
            )
        }
        ?: emptyList(),
    totalPrice = price?.takeIf { it.isNotBlank() },
    priceCurrency = currency?.takeIf { it.isNotBlank() },
    dateIssued = dateIssued?.takeIf { it.isNotBlank() },
    event = event?.toLib(),
    journey = journey?.toLib(),
    validFrom = validFrom?.takeIf { it.isNotBlank() },
    validThrough = validThrough?.takeIf { it.isNotBlank() },
    style = style?.toLib(),
    details = extras.mapIndexed { index, extra -> extra.toLib(index) },
    passTypeIdentifier = passInfo?.passTypeIdentifier?.takeIf { it.isNotBlank() },
    serialNumber = passInfo?.serialNumber?.takeIf { it.isNotBlank() },
    teamIdentifier = passInfo?.teamIdentifier?.takeIf { it.isNotBlank() },
    webServiceUrl = passInfo?.webServiceUrl?.takeIf { it.isNotBlank() },
    authenticationToken = passInfo?.authenticationToken?.takeIf { it.isNotBlank() },
    groupingIdentifier = passInfo?.groupingIdentifier?.takeIf { it.isNotBlank() },
    sharingProhibited = passInfo?.sharingProhibited,
    reservation = reservation?.toLib(),
    membership = membership?.toLib(),
    relevantLocations = locations.mapNotNull { it.toLib() },
    beacons = beacons.mapNotNull { b ->
        LibBeacon(b.proximityUuid, b.major, b.minor, b.relevantText)
            .takeUnless { it.proximityUuid == null && it.relevantText == null }
    },
    wifiNetworks = wifi.filter { it.ssid != null }.map { LibWifi(it.ssid, it.password) },
    voided = voided,
    silenceRequested = silenceRequested,
    relevantStartDate = relevantStart?.takeIf { it.isNotBlank() },
    relevantEndDate = relevantEnd?.takeIf { it.isNotBlank() },
    source = source.toLib(),
)

private fun TicketReservationInfo.toLib(): LibReservation? {
    if (isEmpty) return null
    return LibReservation(
        boardingGroup = boardingGroup?.takeIf { it.isNotBlank() },
        boardingZone = boardingZone?.takeIf { it.isNotBlank() },
        passengerSequenceNumber = sequenceNumber?.takeIf { it.isNotBlank() },
        fareClass = fareClass?.takeIf { it.isNotBlank() },
        passengerPriorityStatus = priorityStatus?.takeIf { it.isNotBlank() },
        securityScreening = securityScreening?.takeIf { it.isNotBlank() },
    )
}

private fun TicketMembershipInfo.toLib(): LibMembership? {
    if (isEmpty) return null
    return LibMembership(
        programName = programName?.takeIf { it.isNotBlank() },
        membershipNumber = number?.takeIf { it.isNotBlank() },
        membershipStatus = status?.takeIf { it.isNotBlank() },
        pointsBalance = pointsBalance?.takeIf { it.isNotBlank() },
        balance = balance?.takeIf { it.isNotBlank() },
        balanceCurrency = balanceCurrency?.takeIf { it.isNotBlank() },
    )
}

private fun TicketLocationInfo.toLib(): LibRelevantLocation? {
    val geo = if (latitude != null || longitude != null) {
        LibGeo(latitude, longitude, altitude)
    } else {
        null
    }
    if (geo == null && relevantText == null) return null
    return LibRelevantLocation(geo = geo, maxDistance = maxDistance, relevantText = relevantText)
}

fun Ticket.toDraft(): TicketDraft = TicketDraft(
    title = title,
    description = description,
    number = number,
    token = token,
    barcodeFormat = barcodeFormat,
    barcodeEncoding = barcodeEncoding,
    barcodeAltText = barcodeAltText,
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
    style = style,
    extras = extras,
    passInfo = passInfo,
    reservation = reservation,
    membership = membership,
    locations = locations,
    beacons = beacons,
    wifi = wifi,
    voided = voided,
    silenceRequested = silenceRequested,
    relevantStart = relevantStart,
    relevantEnd = relevantEnd,
    source = source,
)

private fun LibDetail.toDomain(): TicketExtra? {
    val content = value?.takeIf { it.isNotBlank() } ?: return null
    return TicketExtra(
        label = label?.takeIf { it.isNotBlank() },
        value = content,
        placement = placement?.let { p ->
            runCatching { TicketExtraPlacement.valueOf(p.name) }.getOrNull()
        },
        changeMessage = changeMessage,
        textAlignment = textAlignment,
        linkUrl = linkUrl,
    )
}

private fun TicketExtra.toLib(order: Int): LibDetail = LibDetail(
    label = label?.takeIf { it.isNotBlank() },
    value = value,
    placement = placement?.let { p ->
        runCatching { LibPlacement.valueOf(p.name) }.getOrNull()
    },
    order = order,
    changeMessage = changeMessage?.takeIf { it.isNotBlank() },
    textAlignment = textAlignment?.takeIf { it.isNotBlank() },
    linkUrl = linkUrl?.takeIf { it.isNotBlank() },
)

private fun LibStyle.toDomain(): TicketStyle? = TicketStyle(
    backgroundColor = backgroundColor,
    foregroundColor = foregroundColor,
    labelColor = labelColor,
    stripColor = stripColor,
    footerBackgroundColor = footerBackgroundColor,
    logoText = logoText,
    logoSymbolName = logoSymbolName,
).takeIf { !it.isEmpty }

private fun TicketStyle.toLib(): LibStyle? {
    if (isEmpty) return null
    return LibStyle(
        foregroundColor = foregroundColor?.takeIf { it.isNotBlank() },
        backgroundColor = backgroundColor?.takeIf { it.isNotBlank() },
        labelColor = labelColor?.takeIf { it.isNotBlank() },
        stripColor = stripColor?.takeIf { it.isNotBlank() },
        footerBackgroundColor = footerBackgroundColor?.takeIf { it.isNotBlank() },
        logoText = logoText?.takeIf { it.isNotBlank() },
        logoSymbolName = logoSymbolName?.takeIf { it.isNotBlank() },
    )
}

private fun LibEvent.toDomain(): TicketEventInfo = TicketEventInfo(
    name = name,
    start = startDate,
    end = endDate,
    doorTime = doorTime,
    venue = location?.let { TicketVenue(it.name, it.address) },
    performers = performers,
)

private fun TicketEventInfo.toLib(): LibEvent? {
    val hasContent = name != null || start != null || end != null || venue != null ||
        performers.isNotEmpty()
    if (!hasContent) return null
    return LibEvent(
        name = name?.takeIf { it.isNotBlank() },
        startDate = start?.takeIf { it.isNotBlank() },
        endDate = end?.takeIf { it.isNotBlank() },
        doorTime = doorTime?.takeIf { it.isNotBlank() },
        location = venue?.takeIf { it.name != null || it.address != null }
            ?.let { LibPlace(name = it.name, address = it.address) },
        performers = performers.filter { it.isNotBlank() },
    )
}

private fun LibJourney.toDomain(): TicketJourney = TicketJourney(
    mode = runCatching { TransportMode.valueOf(mode.name) }.getOrDefault(TransportMode.FLIGHT),
    carrier = carrierName,
    serviceNumber = serviceNumber,
    from = departure?.toDomainStop(departureTime),
    to = arrival?.toDomainStop(arrivalTime),
    boardingTime = boardingTime,
    transitStatus = transitStatus,
    transitStatusReason = transitStatusReason,
    vehicleName = vehicleName,
    vehicleNumber = vehicleNumber,
    vehicleType = vehicleType,
    coachNumber = coachNumber,
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
        boardingTime = boardingTime?.takeIf { it.isNotBlank() },
        transitStatus = transitStatus?.takeIf { it.isNotBlank() },
        transitStatusReason = transitStatusReason?.takeIf { it.isNotBlank() },
        vehicleName = vehicleName?.takeIf { it.isNotBlank() },
        vehicleNumber = vehicleNumber?.takeIf { it.isNotBlank() },
        vehicleType = vehicleType?.takeIf { it.isNotBlank() },
        coachNumber = coachNumber?.takeIf { it.isNotBlank() },
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

fun TicketDraft.toProvisionalTicket(uri: String): Ticket = Ticket(
    uri = uri,
    title = title,
    description = description,
    number = number,
    token = token,
    barcodeFormat = barcodeFormat,
    barcodeEncoding = barcodeEncoding,
    barcodeAltText = barcodeAltText,
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
    style = style,
    extras = extras,
    passInfo = passInfo,
    reservation = reservation,
    membership = membership,
    locations = locations,
    beacons = beacons,
    wifi = wifi,
    voided = voided,
    silenceRequested = silenceRequested,
    relevantStart = relevantStart,
    relevantEnd = relevantEnd,
    source = source,
)

fun Ticket.applying(draft: TicketDraft): Ticket = copy(
    title = draft.title,
    description = draft.description,
    number = draft.number,
    token = draft.token,
    barcodeFormat = draft.barcodeFormat,
    barcodeEncoding = draft.barcodeEncoding,
    barcodeAltText = draft.barcodeAltText,
    category = draft.category,
    issuer = draft.issuer,
    holder = draft.holder,
    seat = draft.seat,
    price = draft.price,
    currency = draft.currency,
    dateIssued = draft.dateIssued,
    event = draft.event,
    journey = draft.journey,
    validFrom = draft.validFrom,
    validThrough = draft.validThrough,
    style = draft.style,
    extras = draft.extras,
    passInfo = draft.passInfo,
    reservation = draft.reservation,
    membership = draft.membership,
    locations = draft.locations,
    beacons = draft.beacons,
    wifi = draft.wifi,
    voided = draft.voided,
    silenceRequested = draft.silenceRequested,
    relevantStart = draft.relevantStart,
    relevantEnd = draft.relevantEnd,
    source = draft.source,
)
