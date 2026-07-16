package com.erfangholami.solidshare.presentation.wallet

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Canvas
import com.erfangholami.solidshare.R
import com.erfangholami.solidshare.data.passimport.BcbpParser
import com.erfangholami.solidshare.data.passimport.PassImages
import com.erfangholami.solidshare.domain.model.Ticket
import com.erfangholami.solidshare.domain.model.TicketBarcodeFormat
import com.erfangholami.solidshare.domain.model.TicketCategory
import com.erfangholami.solidshare.domain.model.TicketDraft
import com.erfangholami.solidshare.domain.model.TicketEventInfo
import com.erfangholami.solidshare.domain.model.TicketExtra
import com.erfangholami.solidshare.domain.model.TicketExtraPlacement
import com.erfangholami.solidshare.domain.model.TicketJourney
import com.erfangholami.solidshare.domain.model.TicketSeatInfo
import com.erfangholami.solidshare.domain.model.TicketStop
import com.erfangholami.solidshare.domain.model.TicketStyle
import com.erfangholami.solidshare.domain.model.TicketSummaryItem
import com.erfangholami.solidshare.domain.model.TicketVenue
import com.erfangholami.solidshare.domain.model.TransportMode
import com.erfangholami.solidshare.presentation.theme.AppTheme
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

data class PassCardData(
    val title: String,
    val category: TicketCategory,
    val issuer: String? = null,
    val style: TicketStyle? = null,
    val start: String? = null,
    val event: TicketEventInfo? = null,
    val journey: TicketJourney? = null,
    val seat: TicketSeatInfo? = null,
    val holder: String? = null,
    val number: String? = null,
    val validThrough: String? = null,
    val token: String? = null,
    val barcodeFormat: TicketBarcodeFormat = TicketBarcodeFormat.NONE,
    val images: PassImages? = null,
    val extras: List<TicketExtra> = emptyList(),
    val barcodeEncoding: String? = null,
    val barcodeAltText: String? = null,
)

private fun PassCardData.headerExtras(): List<TicketExtra> = extras
    .filter { it.placement == TicketExtraPlacement.HEADER && !it.label.isNullOrBlank() }
    .take(2)

private fun PassCardData.bodyExtraList(): List<TicketExtra> {
    val header = headerExtras()
    return extras.filter {
        it.placement != TicketExtraPlacement.BACK && it !in header && !it.label.isNullOrBlank()
    }
}

@Composable
private fun PassCardData.bodyFields(): List<Pair<String, String>> =
    bodyExtraList().map { normalizedExtra(category, it) }

fun TicketDraft.toPassCardData(images: PassImages? = null): PassCardData = PassCardData(
    title = title,
    category = category,
    issuer = issuer,
    style = style,
    start = event?.start ?: journey?.from?.time,
    event = event,
    journey = journey,
    seat = seat,
    holder = holder,
    number = number,
    validThrough = validThrough,
    token = token,
    barcodeFormat = barcodeFormat,
    images = images,
    extras = extras,
    barcodeEncoding = barcodeEncoding,
    barcodeAltText = barcodeAltText,
)

fun Ticket.toPassCardData(images: PassImages? = null): PassCardData = PassCardData(
    title = title,
    category = category,
    issuer = issuer,
    style = style,
    start = event?.start ?: journey?.from?.time,
    event = event,
    journey = journey,
    seat = seat,
    holder = holder,
    number = number,
    validThrough = validThrough,
    token = token,
    barcodeFormat = barcodeFormat,
    images = images,
    extras = extras,
    barcodeEncoding = barcodeEncoding,
    barcodeAltText = barcodeAltText,
)

fun TicketSummaryItem.toPassCardData(): PassCardData = PassCardData(
    title = title,
    category = category,
    issuer = issuer,
    style = TicketStyle(backgroundColor = backgroundColor, foregroundColor = foregroundColor)
        .takeIf { !it.isEmpty },
    start = eventStart,
    validThrough = validThrough,
)

data class PassPalette(
    val container: Color,
    val onContainer: Color,
    val label: Color,
)

internal fun parsePassColor(raw: String?): Color? {
    if (raw.isNullOrBlank()) return null
    val value = raw.trim()
    if (value.startsWith("#")) {
        val hex = value.removePrefix("#")
        val parsed = hex.toLongOrNull(16) ?: return null
        return when (hex.length) {
            6 -> Color(0xFF000000 or parsed)
            8 -> Color(parsed.toULong().toLong())
            else -> null
        }
    }
    val match = RGB_PATTERN.find(value) ?: return null
    val (r, g, b) = match.destructured
    val red = r.toIntOrNull()?.coerceIn(0, 255) ?: return null
    val green = g.toIntOrNull()?.coerceIn(0, 255) ?: return null
    val blue = b.toIntOrNull()?.coerceIn(0, 255) ?: return null
    return Color(red, green, blue)
}

private val RGB_PATTERN = Regex("""rgba?\(\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)""")

internal fun passPalette(data: PassCardData, expired: Boolean = false): PassPalette {
    val container = parsePassColor(data.style?.backgroundColor) ?: defaultContainer(data.category)
    val onContainer = parsePassColor(data.style?.foregroundColor) ?: bestOn(container)
    val label = parsePassColor(data.style?.labelColor) ?: onContainer.copy(alpha = 0.72f)
    if (!expired) return PassPalette(container, onContainer, label)
    val gray = Color(0xFF75797E)
    return PassPalette(
        container = lerp(container, gray, 0.62f),
        onContainer = onContainer.copy(alpha = 0.9f),
        label = label.copy(alpha = 0.6f),
    )
}

private fun defaultContainer(category: TicketCategory): Color = when (category) {
    TicketCategory.FLIGHT -> Color(0xFF1F5FA8)
    TicketCategory.TRAIN -> Color(0xFF256E4E)
    TicketCategory.BUS -> Color(0xFF9A5B13)
    TicketCategory.BOAT -> Color(0xFF16697A)
    TicketCategory.EVENT -> Color(0xFF5B3FA8)
    TicketCategory.CINEMA -> Color(0xFFA8323E)
    TicketCategory.LOYALTY -> Color(0xFF00696B)
    TicketCategory.COUPON -> Color(0xFFB04A00)
    TicketCategory.GENERIC -> Color(0xFF3A4753)
}

internal fun bestOn(background: Color): Color =
    if (background.luminance() > 0.5f) Color(0xFF1C1B1F) else Color.White

@Composable
fun PassCard(
    data: PassCardData,
    modifier: Modifier = Modifier,
    expired: Boolean = false,
    showBarcode: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    val palette = remember(data, expired) { passPalette(data, expired) }
    val shape = RoundedCornerShape(20.dp)
    val content: @Composable () -> Unit = {
        Column {
            PassHeader(data, palette, expired)
            PassStrip(data)
            Column(Modifier.padding(horizontal = 18.dp)) {
                when {
                    data.journey != null -> TransitBody(data, data.journey, palette)
                    data.category == TicketCategory.EVENT || data.category == TicketCategory.CINEMA ->
                        EventBody(data, palette)

                    else -> GenericBody(data, palette)
                }
                Spacer(Modifier.height(18.dp))
            }
            if (showBarcode && !data.token.isNullOrBlank()) {
                TearLine(palette)
                Column(
                    Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, bottom = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color.White,
                    ) {
                        Column(
                            Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            TicketBarcode(token = data.token, format = data.barcodeFormat, encoding = data.barcodeEncoding)
                            val caption = remember(data.barcodeAltText, data.token) {
                                barcodeCaption(data.barcodeAltText, data.token)
                            }
                            caption?.let {
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    it,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF3C4043),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    if (onClick != null) {
        Surface(onClick = onClick, modifier = modifier.fillMaxWidth(), shape = shape, color = palette.container) {
            content()
        }
    } else {
        Surface(modifier = modifier.fillMaxWidth(), shape = shape, color = palette.container) {
            content()
        }
    }
}

@Composable
private fun PassHeader(data: PassCardData, palette: PassPalette, expired: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val logo = data.images?.logo
        val logoBitmap = remember(logo) {
            logo?.let { bytes ->
                runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() }.getOrNull()
            }
        }
        if (logoBitmap != null) {
            Image(
                bitmap = logoBitmap,
                contentDescription = null,
                modifier = Modifier.height(26.dp).weight(1f, fill = false),
                contentScale = ContentScale.Fit,
                alignment = Alignment.CenterStart,
            )
            data.style?.logoText?.let {
                Spacer(Modifier.width(10.dp))
                Text(
                    it,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.onContainer,
                )
            }
        } else {
            Text(
                text = data.style?.logoText ?: data.issuer ?: labelFor(data.category),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = palette.onContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
        Spacer(Modifier.weight(1f))
        if (expired) {
            Surface(
                shape = RoundedCornerShape(50),
                color = palette.onContainer.copy(alpha = 0.16f),
            ) {
                Text(
                    stringResource(R.string.wallet_expired_badge),
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.onContainer,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        } else {
            val headerFields = data.headerExtras()
            if (headerFields.isEmpty()) {
                Icon(
                    iconFor(data.category),
                    contentDescription = labelFor(data.category),
                    tint = palette.label,
                    modifier = Modifier.size(18.dp),
                )
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    headerFields.forEach { extra ->
                        val (label, value) = normalizedExtra(data.category, extra)
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                label.uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                color = palette.label,
                            )
                            Text(
                                value,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = palette.onContainer,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PassStrip(data: PassCardData) {
    val strip = data.images?.strip ?: return
    val bitmap = remember(strip) {
        runCatching { BitmapFactory.decodeByteArray(strip, 0, strip.size)?.asImageBitmap() }.getOrNull()
    } ?: return
    Image(
        bitmap = bitmap,
        contentDescription = null,
        modifier = Modifier.fillMaxWidth().height(96.dp),
        contentScale = ContentScale.Crop,
    )
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun TransitBody(data: PassCardData, journey: TicketJourney, palette: PassPalette) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        PassStopColumn(journey.from, palette, alignEnd = false, modifier = Modifier.weight(1f))
        Icon(
            iconFor(data.category),
            contentDescription = null,
            tint = palette.label,
            modifier = Modifier.padding(horizontal = 10.dp).size(22.dp),
        )
        PassStopColumn(journey.to, palette, alignEnd = true, modifier = Modifier.weight(1f))
    }
    Spacer(Modifier.height(16.dp))
    val serviceLabel = when (journey.mode) {
        TransportMode.FLIGHT -> stringResource(R.string.pass_field_flight)
        else -> stringResource(R.string.pass_field_service)
    }
    PassFieldGrid(
        listOfNotNull(
            shortDate(data.start ?: journey.from?.time)?.let {
                stringResource(R.string.pass_field_date) to it
            },
            listOfNotNull(journey.carrier?.takeIf { journey.serviceNumber == null }, journey.serviceNumber)
                .firstOrNull()?.let { serviceLabel to it },
            seatText(data.seat)?.let { stringResource(R.string.pass_field_seat) to it },
            journey.from?.gate?.let { stringResource(R.string.ticket_field_gate) to it },
            journey.from?.platform?.let { stringResource(R.string.ticket_field_platform) to it },
            journey.from?.terminal?.let { stringResource(R.string.ticket_field_terminal) to it },
        ) + data.bodyFields(),
        palette,
    )
}

@Composable
private fun PassStopColumn(
    stop: TicketStop?,
    palette: PassPalette,
    alignEnd: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start) {
        Text(
            stop?.code ?: stop?.name ?: "—",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = palette.onContainer,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        (stop?.cityName ?: stop?.name?.takeIf { stop.code != null })?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = palette.label,
            )
        }
        shortTime(stop?.time)?.let {
            Text(
                it,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color = palette.onContainer,
            )
        }
    }
}

@Composable
private fun EventBody(data: PassCardData, palette: PassPalette) {
    Text(
        data.event?.name ?: data.title,
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.SemiBold,
        color = palette.onContainer,
    )
    data.event?.venue?.let { venue ->
        Spacer(Modifier.height(4.dp))
        venue.name?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = palette.onContainer)
        }
        venue.address?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = palette.label)
        }
    }
    Spacer(Modifier.height(16.dp))
    PassFieldGrid(
        listOfNotNull(
            shortDateTime(data.start)?.let { stringResource(R.string.pass_field_date) to it },
            data.seat?.section?.let { stringResource(R.string.ticket_field_seat_section) to it },
            data.seat?.row?.let { stringResource(R.string.ticket_field_seat_row) to it },
            data.seat?.number?.let { stringResource(R.string.pass_field_seat) to it },
        ) + data.bodyFields(),
        palette,
    )
}

@Composable
private fun GenericBody(data: PassCardData, palette: PassPalette) {
    Text(
        data.title,
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.SemiBold,
        color = palette.onContainer,
    )
    Spacer(Modifier.height(16.dp))
    PassFieldGrid(
        listOfNotNull(
            data.number?.let { stringResource(R.string.pass_field_number) to it },
            data.holder?.let { stringResource(R.string.pass_field_holder) to it },
            (shortDate(data.validThrough) ?: shortDate(data.start))?.let {
                stringResource(R.string.pass_field_valid_until) to it
            },
        ) + data.bodyFields(),
        palette,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PassFieldGrid(fields: List<Pair<String, String>>, palette: PassPalette) {
    val visible = fields.filter { it.second.isNotBlank() }
    if (visible.isEmpty()) return
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        visible.forEach { (label, value) ->
            Column {
                if (label.isNotBlank()) {
                    Text(
                        label.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.label,
                    )
                }
                Text(
                    value,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.onContainer,
                )
            }
        }
    }
}

@Composable
private fun TearLine(palette: PassPalette, notchColor: Color = MaterialTheme.colorScheme.background) {
    Box(Modifier.fillMaxWidth().height(20.dp)) {
        Canvas(Modifier.fillMaxWidth().height(20.dp)) {
            val centerY = size.height / 2f
            val radius = 10.dp.toPx()
            drawCircle(color = notchColor, radius = radius, center = Offset(0f, centerY))
            drawCircle(color = notchColor, radius = radius, center = Offset(size.width, centerY))
            drawLine(
                color = palette.label.copy(alpha = 0.5f),
                start = Offset(radius + 8.dp.toPx(), centerY),
                end = Offset(size.width - radius - 8.dp.toPx(), centerY),
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f)),
            )
        }
    }
}

@Composable
private fun seatText(seat: TicketSeatInfo?): String? {
    seat ?: return null
    return listOfNotNull(seat.row, seat.number).joinToString("").ifBlank { null }
        ?: seat.section
}

internal fun barcodeCaption(altText: String?, token: String?): String? {
    altText?.takeIf { it.isNotBlank() }?.let { return it }
    token ?: return null
    return BcbpParser.parse(token)?.number
}

private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM")
private val dateTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM · HH:mm")

internal fun shortTime(iso: String?): String? {
    iso ?: return null
    runCatching { OffsetDateTime.parse(iso).format(timeFormatter) }.getOrNull()?.let { return it }
    runCatching { LocalDateTime.parse(iso).format(timeFormatter) }.getOrNull()?.let { return it }
    runCatching { LocalDate.parse(iso).format(dateFormatter) }.getOrNull()?.let { return it }
    return iso.takeIf { DISPLAY_TIME.matches(it.trim()) }?.trim()
}

private val DISPLAY_TIME = Regex("""\d{1,2}:\d{2}( ?[APap][Mm])?""")

internal fun shortDate(iso: String?): String? {
    iso ?: return null
    runCatching { OffsetDateTime.parse(iso).format(dateFormatter) }.getOrNull()?.let { return it }
    runCatching { LocalDateTime.parse(iso).format(dateFormatter) }.getOrNull()?.let { return it }
    return runCatching { LocalDate.parse(iso).format(dateFormatter) }.getOrNull()
}

internal fun shortDateTime(iso: String?): String? {
    iso ?: return null
    runCatching { OffsetDateTime.parse(iso).format(dateTimeFormatter) }.getOrNull()?.let { return it }
    runCatching { LocalDateTime.parse(iso).format(dateTimeFormatter) }.getOrNull()?.let { return it }
    return runCatching { LocalDate.parse(iso).format(dateFormatter) }.getOrNull()
}

private fun sampleFlight(): PassCardData = TicketDraft(
    title = "AMS → JFK",
    category = TicketCategory.FLIGHT,
    issuer = "KLM Royal Dutch Airlines",
    holder = "Erfan Gholami",
    number = "ABC123",
    token = "M1GHOLAMI/ERFAN",
    barcodeFormat = TicketBarcodeFormat.PDF_417,
    seat = TicketSeatInfo(number = "27A"),
    style = TicketStyle(backgroundColor = "rgb(0,54,113)", logoText = "KLM"),
    journey = TicketJourney(
        mode = TransportMode.FLIGHT,
        carrier = "KL",
        serviceNumber = "KL641",
        from = TicketStop(code = "AMS", cityName = "Amsterdam", time = "2026-09-01T09:40+02:00", gate = "D07"),
        to = TicketStop(code = "JFK", cityName = "New York", time = "2026-09-01T12:25-04:00"),
    ),
).toPassCardData()

private fun sampleEvent(): PassCardData = TicketDraft(
    title = "Solid World Meetup",
    category = TicketCategory.EVENT,
    issuer = "Eventix",
    token = "EVT-778899",
    barcodeFormat = TicketBarcodeFormat.QR_CODE,
    seat = TicketSeatInfo(number = "12", row = "F", section = "Balcony"),
    event = TicketEventInfo(
        name = "Solid World Meetup",
        start = "2026-08-20T19:30+02:00",
        venue = TicketVenue(name = "Paradiso", address = "Weteringschans 6, Amsterdam"),
    ),
).toPassCardData()

@Preview(showBackground = true, widthDp = 380)
@Composable
private fun PassCardFlightPreview() {
    AppTheme {
        Column(Modifier.padding(16.dp)) {
            PassCard(sampleFlight())
        }
    }
}

@Preview(showBackground = true, widthDp = 380)
@Composable
private fun PassCardEventPreview() {
    AppTheme {
        Column(Modifier.padding(16.dp)) {
            PassCard(sampleEvent())
        }
    }
}

@Preview(showBackground = true, widthDp = 380)
@Composable
private fun PassCardExpiredPreview() {
    AppTheme {
        Column(Modifier.padding(16.dp)) {
            PassCard(sampleFlight(), expired = true, showBarcode = false)
        }
    }
}

@Preview(showBackground = true, widthDp = 380)
@Composable
private fun PassCardLoyaltyPreview() {
    AppTheme {
        Column(Modifier.padding(16.dp)) {
            PassCard(
                TicketDraft(
                    title = "Coffee Club",
                    category = TicketCategory.LOYALTY,
                    issuer = "Bocca Coffee",
                    number = "9917",
                    holder = "Erfan Gholami",
                    token = "LOY-9917",
                    barcodeFormat = TicketBarcodeFormat.CODE_128,
                ).toPassCardData(),
                showBarcode = false,
            )
        }
    }
}
