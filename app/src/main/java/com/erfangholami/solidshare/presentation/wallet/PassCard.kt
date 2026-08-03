package com.erfangholami.solidshare.presentation.wallet

import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.erfangholami.solidshare.R
import com.erfangholami.solidshare.data.passimport.BcbpParser
import com.erfangholami.solidshare.data.passimport.PassImages
import com.erfangholami.solidshare.data.passimport.normalizePassDateTime
import com.erfangholami.solidshare.data.passimport.normalizePassKey
import com.erfangholami.solidshare.domain.model.Ticket
import com.erfangholami.solidshare.domain.model.TicketBarcodeFormat
import com.erfangholami.solidshare.domain.model.TicketCategory
import com.erfangholami.solidshare.domain.model.TicketDraft
import com.erfangholami.solidshare.domain.model.TicketEventInfo
import com.erfangholami.solidshare.domain.model.TicketExtra
import com.erfangholami.solidshare.domain.model.TicketExtraPlacement
import com.erfangholami.solidshare.domain.model.TicketJourney
import com.erfangholami.solidshare.domain.model.TicketMembershipInfo
import com.erfangholami.solidshare.domain.model.TicketReservationInfo
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
    val reservation: TicketReservationInfo? = null,
    val membership: TicketMembershipInfo? = null,
)

private enum class PassLayout { BOARDING, COUPON, EVENT, STORE, GENERIC }

private fun layoutFor(category: TicketCategory): PassLayout = when (category) {
    TicketCategory.FLIGHT,
    TicketCategory.TRAIN,
    TicketCategory.BUS,
    TicketCategory.BOAT,
    -> PassLayout.BOARDING

    TicketCategory.EVENT, TicketCategory.CINEMA -> PassLayout.EVENT
    TicketCategory.LOYALTY -> PassLayout.STORE
    TicketCategory.COUPON -> PassLayout.COUPON
    TicketCategory.GENERIC -> PassLayout.GENERIC
}

private data class PassField(
    val label: String,
    val value: String,
    val alignment: String? = null,
)

@Composable
private fun PassCardData.field(extra: TicketExtra): PassField {
    val (label, value) = normalizedExtra(category, extra)
    return PassField(label, value, extra.textAlignment)
}

private fun PassCardData.tier(placement: TicketExtraPlacement): List<TicketExtra> =
    extras.filter { it.placement == placement }

private fun PassCardData.hasAuthoredBody(): Boolean = extras.any {
    it.placement == TicketExtraPlacement.PRIMARY ||
        it.placement == TicketExtraPlacement.SECONDARY ||
        it.placement == TicketExtraPlacement.AUXILIARY
}

private fun PassCardData.headerExtras(): List<TicketExtra> = extras
    .filter { it.placement == TicketExtraPlacement.HEADER && !it.label.isNullOrBlank() }
    .take(3)

@Composable
private fun PassCardData.primaryFields(): List<PassField> {
    if (hasAuthoredBody()) return tier(TicketExtraPlacement.PRIMARY).map { field(it) }
    return synthesizedPrimary()
}

@Composable
private fun PassCardData.secondaryFields(): List<PassField> {
    if (hasAuthoredBody()) return tier(TicketExtraPlacement.SECONDARY).map { field(it) }.take(5)
    return synthesizedSecondary()
}

@Composable
private fun PassCardData.auxiliaryFields(): List<PassField> {
    if (hasAuthoredBody()) return tier(TicketExtraPlacement.AUXILIARY).map { field(it) }.take(5)
    return synthesizedAuxiliary()
}

@Composable
private fun PassCardData.footerFields(): List<PassField> =
    tier(TicketExtraPlacement.FOOTER).map { field(it) }.take(4)

@Composable
private fun PassCardData.synthesizedPrimary(): List<PassField> = when (layoutFor(category)) {
    PassLayout.BOARDING -> emptyList()

    PassLayout.EVENT -> listOf(PassField("", event?.name ?: title))

    PassLayout.STORE -> listOfNotNull(
        membership?.balanceText()?.let {
            PassField(stringResource(R.string.pass_label_balance), it)
        },
    ).ifEmpty { listOf(PassField("", title)) }

    PassLayout.COUPON, PassLayout.GENERIC -> listOf(PassField("", title))
}

@Composable
private fun PassCardData.synthesizedSecondary(): List<PassField> = when (layoutFor(category)) {
    PassLayout.BOARDING -> listOfNotNull(
        seatText(seat)?.let { PassField(stringResource(R.string.pass_field_seat), it) },
        journey?.from?.gate?.let { PassField(stringResource(R.string.ticket_field_gate), it) },
        journey?.from?.platform?.let {
            PassField(stringResource(R.string.ticket_field_platform), it)
        },
        journey?.from?.terminal?.let {
            PassField(stringResource(R.string.ticket_field_terminal), it)
        },
    )

    PassLayout.EVENT -> listOfNotNull(
        shortDateTime(start)?.let { PassField(stringResource(R.string.pass_field_date), it) },
    )

    PassLayout.STORE -> listOfNotNull(
        number?.let { PassField(stringResource(R.string.pass_field_number), it) },
        holder?.let { PassField(stringResource(R.string.pass_field_holder), it) },
        shortDate(validThrough)?.let {
            PassField(stringResource(R.string.pass_field_valid_until), it)
        },
    )

    PassLayout.COUPON -> listOfNotNull(
        shortDate(validThrough)?.let {
            PassField(stringResource(R.string.pass_field_valid_until), it)
        },
        number?.let { PassField(stringResource(R.string.pass_field_number), it) },
    )

    PassLayout.GENERIC -> listOfNotNull(
        number?.let { PassField(stringResource(R.string.pass_field_number), it) },
        holder?.let { PassField(stringResource(R.string.pass_field_holder), it) },
    )
}

@Composable
private fun PassCardData.synthesizedAuxiliary(): List<PassField> = when (layoutFor(category)) {
    PassLayout.BOARDING -> {
        val serviceLabel = when (journey?.mode) {
            TransportMode.FLIGHT -> stringResource(R.string.pass_field_flight)
            else -> stringResource(R.string.pass_field_service)
        }
        listOfNotNull(
            shortDate(start ?: journey?.from?.time)?.let {
                PassField(stringResource(R.string.pass_field_date), it)
            },
            listOfNotNull(
                journey?.carrier?.takeIf { journey.serviceNumber == null },
                journey?.serviceNumber,
            ).firstOrNull()?.let { PassField(serviceLabel, it) },
        ) + typedFields()
    }

    PassLayout.EVENT -> listOfNotNull(
        seat?.section?.let { PassField(stringResource(R.string.ticket_field_seat_section), it) },
        seat?.row?.let { PassField(stringResource(R.string.ticket_field_seat_row), it) },
        seat?.number?.let { PassField(stringResource(R.string.pass_field_seat), it) },
    ) + typedFields()

    PassLayout.STORE, PassLayout.COUPON, PassLayout.GENERIC -> when (layoutFor(category)) {
        PassLayout.GENERIC -> listOfNotNull(
            shortDate(validThrough)?.let {
                PassField(stringResource(R.string.pass_field_valid_until), it)
            },
        ) + typedFields()

        else -> typedFields()
    }
}

@Composable
private fun PassCardData.typedFields(): List<PassField> {
    val labels = extras.mapNotNull { it.label?.let(::normalizePassKey) }.toSet()
    fun free(vararg keys: String) = keys.none { normalizePassKey(it) in labels }
    return listOfNotNull(
        reservation?.boardingGroup
            ?.takeIf { free("group", "boardinggroup") }
            ?.let { PassField(stringResource(R.string.pass_label_group), it) },
        reservation?.boardingZone
            ?.takeIf { free("zone", "boardingzone") }
            ?.let { PassField(stringResource(R.string.pass_label_zone), it) },
        reservation?.sequenceNumber
            ?.takeIf { free("sequence", "sequencenumber", "seq") }
            ?.let { PassField(stringResource(R.string.pass_label_sequence), it) },
        reservation?.fareClass
            ?.takeIf { free("class", "fareclass", "cabin") }
            ?.let { PassField(stringResource(R.string.pass_label_class), it) },
        membership?.balanceText()
            ?.takeIf { layoutFor(category) != PassLayout.STORE }
            ?.takeIf { free("balance", "points", "pointsbalance") }
            ?.let { PassField(stringResource(R.string.pass_label_balance), it) },
    )
}

internal fun TicketMembershipInfo.balanceText(): String? =
    pointsBalance ?: balance?.let { listOfNotNull(it, balanceCurrency).joinToString(" ") }

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
    reservation = reservation,
    membership = membership,
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
    reservation = reservation,
    membership = membership,
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
            3 -> {
                val expanded = hex.map { "$it$it" }.joinToString("").toLong(16)
                Color(0xFF000000 or expanded)
            }

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
    val container = parsePassColor(data.style?.backgroundColor)
        ?: parsePassColor(data.style?.stripColor)
        ?: defaultContainer(data.category)
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
    val layout = layoutFor(data.category)
    val strip = rememberPassBitmap(data.images?.strip)
    val background = if (layout == PassLayout.EVENT && strip == null) {
        rememberPassBitmap(data.images?.background)
    } else {
        null
    }
    val content: @Composable () -> Unit = {
        Box {
            background?.let {
                Image(
                    bitmap = it,
                    contentDescription = null,
                    modifier = Modifier.matchParentSize().blur(18.dp),
                    contentScale = ContentScale.Crop,
                    alpha = 0.45f,
                )
            }
            Column {
                PassHeader(data, palette, expired)
                when (layout) {
                    PassLayout.BOARDING -> BoardingBody(data, palette)

                    PassLayout.COUPON, PassLayout.STORE ->
                        StripBody(data, palette, strip, COUPON_STRIP_ASPECT, combineRows = true)

                    PassLayout.EVENT ->
                        if (strip != null || parsePassColor(data.style?.stripColor) != null) {
                            StripBody(data, palette, strip, EVENT_STRIP_ASPECT, combineRows = true)
                        } else {
                            EventBody(data, palette)
                        }

                    PassLayout.GENERIC -> GenericBody(data, palette)
                }
                val footerFields = data.footerFields()
                if (footerFields.isNotEmpty()) {
                    FieldRow(
                        footerFields,
                        palette,
                        modifier = Modifier.padding(horizontal = 18.dp),
                    )
                }
                Spacer(Modifier.height(16.dp))
                if (showBarcode && !data.token.isNullOrBlank()) {
                    rememberPassBitmap(data.images?.footer)?.let { footer ->
                        Image(
                            bitmap = footer,
                            contentDescription = null,
                            modifier = Modifier.fillMaxWidth().height(20.dp),
                            contentScale = ContentScale.Fit,
                        )
                    }
                    TearLine(palette)
                    BarcodePanel(data)
                }
            }
        }
    }
    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = modifier.fillMaxWidth(),
            shape = shape,
            color = palette.container,
        ) {
            content()
        }
    } else {
        Surface(modifier = modifier.fillMaxWidth(), shape = shape, color = palette.container) {
            content()
        }
    }
}

private const val COUPON_STRIP_ASPECT = 375f / 144f
private const val EVENT_STRIP_ASPECT = 375f / 98f

@Composable
private fun BarcodePanel(data: PassCardData) {
    val footerBackground = parsePassColor(data.style?.footerBackgroundColor)
    Column(
        Modifier
            .fillMaxWidth()
            .then(footerBackground?.let { Modifier.background(it) } ?: Modifier)
            .padding(start = 18.dp, end = 18.dp, bottom = 18.dp),
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
                TicketBarcode(
                    token = data.token.orEmpty(),
                    format = data.barcodeFormat,
                    encoding = data.barcodeEncoding,
                )
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

@Composable
private fun rememberPassBitmap(bytes: ByteArray?): ImageBitmap? {
    val bitmap by produceState<ImageBitmap?>(null, bytes) {
        value = bytes?.let {
            withContext(Dispatchers.Default) {
                runCatching {
                    BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap()
                }.getOrNull()
            }
        }
    }
    return bitmap
}

@Composable
private fun PassHeader(data: PassCardData, palette: PassPalette, expired: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val logoBitmap = rememberPassBitmap(data.images?.logo)
        if (logoBitmap != null) {
            Image(
                bitmap = logoBitmap,
                contentDescription = null,
                modifier = Modifier.height(27.dp).weight(1f, fill = false),
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
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
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
                        val field = data.field(extra)
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                field.label.uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                color = palette.label,
                            )
                            Text(
                                field.value,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = palette.onContainer,
                                textAlign = TextAlign.End,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BoardingBody(data: PassCardData, palette: PassPalette) {
    Column(Modifier.padding(horizontal = 18.dp)) {
        val primaries = if (data.hasAuthoredBody()) {
            data.tier(TicketExtraPlacement.PRIMARY).map { data.field(it) }
        } else {
            emptyList()
        }
        when {
            primaries.size >= 2 -> Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BigEndpoint(primaries.first(), palette, alignEnd = false, Modifier.weight(1f))
                Icon(
                    iconFor(data.category),
                    contentDescription = null,
                    tint = palette.label,
                    modifier = Modifier.padding(horizontal = 10.dp).size(22.dp),
                )
                BigEndpoint(primaries.last(), palette, alignEnd = true, Modifier.weight(1f))
            }

            data.journey != null -> Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PassStopColumn(
                    data.journey.from,
                    palette,
                    alignEnd = false,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    iconFor(data.category),
                    contentDescription = null,
                    tint = palette.label,
                    modifier = Modifier.padding(horizontal = 10.dp).size(22.dp),
                )
                PassStopColumn(
                    data.journey.to,
                    palette,
                    alignEnd = true,
                    modifier = Modifier.weight(1f),
                )
            }

            primaries.isNotEmpty() -> BigEndpoint(
                primaries.first(),
                palette,
                alignEnd = false,
                Modifier.fillMaxWidth(),
            )

            else -> Text(
                data.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = palette.onContainer,
            )
        }
        Spacer(Modifier.height(16.dp))
        val auxiliary = data.auxiliaryFields()
        if (auxiliary.isNotEmpty()) {
            FieldRow(auxiliary, palette)
            Spacer(Modifier.height(12.dp))
        }
        val secondary = data.secondaryFields()
        if (secondary.isNotEmpty()) {
            FieldRow(secondary, palette)
        }
    }
}

@Composable
private fun BigEndpoint(
    field: PassField,
    palette: PassPalette,
    alignEnd: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start,
    ) {
        if (field.label.isNotBlank()) {
            Text(
                field.label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = palette.label,
            )
        }
        Text(
            field.value,
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = palette.onContainer,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun PassStopColumn(
    stop: TicketStop?,
    palette: PassPalette,
    alignEnd: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start,
    ) {
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
private fun StripBody(
    data: PassCardData,
    palette: PassPalette,
    strip: ImageBitmap?,
    aspect: Float,
    combineRows: Boolean,
) {
    val stripColor = parsePassColor(data.style?.stripColor)
    val primaries = data.primaryFields()
    when {
        strip != null -> Box(Modifier.fillMaxWidth().aspectRatio(aspect)) {
            Image(
                bitmap = strip,
                contentDescription = null,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop,
            )
            Box(
                Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.55f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.45f),
                        ),
                    ),
            )
            PrimaryOverlay(
                primaries,
                palette.copy(onContainer = Color.White, label = Color.White.copy(alpha = 0.8f)),
                Modifier.align(Alignment.BottomStart),
            )
        }

        stripColor != null -> Box(
            Modifier
                .fillMaxWidth()
                .height(if (aspect == EVENT_STRIP_ASPECT) 72.dp else 96.dp)
                .background(stripColor),
        ) {
            val onStrip = bestOn(stripColor)
            PrimaryOverlay(
                primaries,
                palette.copy(onContainer = onStrip, label = onStrip.copy(alpha = 0.72f)),
                Modifier.align(Alignment.BottomStart),
            )
        }

        else -> Column(Modifier.padding(horizontal = 18.dp)) {
            PrimaryBlock(primaries, data, palette)
        }
    }
    Spacer(Modifier.height(12.dp))
    Column(Modifier.padding(horizontal = 18.dp)) {
        if (combineRows) {
            val combined = (data.secondaryFields() + data.auxiliaryFields()).take(4)
            if (combined.isNotEmpty()) {
                FieldRow(combined, palette)
            }
        } else {
            val secondary = data.secondaryFields()
            if (secondary.isNotEmpty()) {
                FieldRow(secondary, palette)
                Spacer(Modifier.height(12.dp))
            }
            val auxiliary = data.auxiliaryFields()
            if (auxiliary.isNotEmpty()) {
                FieldRow(auxiliary, palette)
            }
        }
    }
}

@Composable
private fun PrimaryOverlay(
    fields: List<PassField>,
    palette: PassPalette,
    modifier: Modifier = Modifier,
) {
    Column(modifier.padding(horizontal = 18.dp, vertical = 12.dp)) {
        fields.take(1).forEach { field ->
            if (field.label.isNotBlank()) {
                Text(
                    field.label.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.label,
                )
            }
            Text(
                field.value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = palette.onContainer,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun PrimaryBlock(
    fields: List<PassField>,
    data: PassCardData,
    palette: PassPalette,
) {
    fields.take(1).forEach { field ->
        if (field.label.isNotBlank()) {
            Text(
                field.label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = palette.label,
            )
        }
        Text(
            field.value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = palette.onContainer,
        )
    }
    if (fields.isEmpty()) {
        Text(
            data.title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = palette.onContainer,
        )
    }
}

@Composable
private fun EventBody(data: PassCardData, palette: PassPalette) {
    Column(Modifier.padding(horizontal = 18.dp)) {
        val thumbnail = rememberPassBitmap(data.images?.thumbnail)
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                PrimaryBlock(data.primaryFields(), data, palette)
                if (!data.hasAuthoredBody()) {
                    data.event?.venue?.let { venue ->
                        Spacer(Modifier.height(4.dp))
                        venue.name?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.bodyMedium,
                                color = palette.onContainer,
                            )
                        }
                        venue.address?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.bodySmall,
                                color = palette.label,
                            )
                        }
                    }
                }
            }
            thumbnail?.let {
                Spacer(Modifier.width(12.dp))
                Image(
                    bitmap = it,
                    contentDescription = null,
                    modifier = Modifier.size(72.dp).clip(RoundedCornerShape(10.dp)),
                    contentScale = ContentScale.Crop,
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        val secondary = data.secondaryFields()
        if (secondary.isNotEmpty()) {
            FieldRow(secondary, palette)
            Spacer(Modifier.height(12.dp))
        }
        val auxiliary = data.auxiliaryFields()
        if (auxiliary.isNotEmpty()) {
            FieldRow(auxiliary, palette)
        }
    }
}

@Composable
private fun GenericBody(data: PassCardData, palette: PassPalette) {
    Column(Modifier.padding(horizontal = 18.dp)) {
        val thumbnail = rememberPassBitmap(data.images?.thumbnail)
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                PrimaryBlock(data.primaryFields(), data, palette)
            }
            thumbnail?.let {
                Spacer(Modifier.width(12.dp))
                Image(
                    bitmap = it,
                    contentDescription = null,
                    modifier = Modifier.size(72.dp).clip(RoundedCornerShape(10.dp)),
                    contentScale = ContentScale.Crop,
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        val secondary = data.secondaryFields()
        if (secondary.isNotEmpty()) {
            FieldRow(secondary, palette)
            Spacer(Modifier.height(12.dp))
        }
        val auxiliary = data.auxiliaryFields()
        if (auxiliary.isNotEmpty()) {
            FieldRow(auxiliary, palette)
        }
    }
}

@Composable
private fun FieldRow(
    fields: List<PassField>,
    palette: PassPalette,
    modifier: Modifier = Modifier,
) {
    val visible = fields.filter { it.value.isNotBlank() }
    if (visible.isEmpty()) return
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        visible.forEachIndexed { index, field ->
            val positionAlignment = when {
                visible.size == 1 -> Alignment.Start
                index == 0 -> Alignment.Start
                index == visible.lastIndex -> Alignment.End
                else -> Alignment.CenterHorizontally
            }
            val alignment = when (field.alignment) {
                "PKTextAlignmentLeft" -> Alignment.Start
                "PKTextAlignmentCenter" -> Alignment.CenterHorizontally
                "PKTextAlignmentRight" -> Alignment.End
                else -> positionAlignment
            }
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = alignment,
            ) {
                if (field.label.isNotBlank()) {
                    Text(
                        field.label.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.label,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    field.value,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.onContainer,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
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

internal fun shortTime(raw: String?): String? {
    val iso = raw?.let(::normalizePassDateTime) ?: return null
    runCatching { OffsetDateTime.parse(iso).format(timeFormatter) }.getOrNull()?.let { return it }
    runCatching { LocalDateTime.parse(iso).format(timeFormatter) }.getOrNull()?.let { return it }
    runCatching { LocalDate.parse(iso).format(dateFormatter) }.getOrNull()?.let { return it }
    return iso.takeIf { DISPLAY_TIME.matches(it.trim()) }?.trim()
}

private val DISPLAY_TIME = Regex("""\d{1,2}:\d{2}( ?[APap][Mm])?""")

internal fun shortDate(raw: String?): String? {
    val iso = raw?.let(::normalizePassDateTime) ?: return null
    runCatching { OffsetDateTime.parse(iso).format(dateFormatter) }.getOrNull()?.let { return it }
    runCatching { LocalDateTime.parse(iso).format(dateFormatter) }.getOrNull()?.let { return it }
    return runCatching { LocalDate.parse(iso).format(dateFormatter) }.getOrNull()
}

internal fun shortDateTime(raw: String?): String? {
    val iso = raw?.let(::normalizePassDateTime) ?: return null
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
        from = TicketStop(
            code = "AMS",
            cityName = "Amsterdam",
            time = "2026-09-01T09:40+02:00",
            gate = "D07",
        ),
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

private fun sampleStoreCard(): PassCardData = TicketDraft(
    title = "Coffee Club",
    category = TicketCategory.LOYALTY,
    issuer = "Bocca Coffee",
    number = "9917",
    holder = "Erfan Gholami",
    token = "LOY-9917",
    barcodeFormat = TicketBarcodeFormat.CODE_128,
    style = TicketStyle(backgroundColor = "#00696B", stripColor = "#00565A"),
    membership = TicketMembershipInfo(pointsBalance = "1 250 points"),
).toPassCardData()

private fun sampleCoupon(): PassCardData = TicketDraft(
    title = "25% off espresso beans",
    category = TicketCategory.COUPON,
    issuer = "Bocca Coffee",
    validThrough = "2026-12-31",
    token = "SAVE25",
    barcodeFormat = TicketBarcodeFormat.QR_CODE,
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
private fun PassCardStoreCardPreview() {
    AppTheme {
        Column(Modifier.padding(16.dp)) {
            PassCard(sampleStoreCard())
        }
    }
}

@Preview(showBackground = true, widthDp = 380)
@Composable
private fun PassCardCouponPreview() {
    AppTheme {
        Column(Modifier.padding(16.dp)) {
            PassCard(sampleCoupon(), showBarcode = false)
        }
    }
}
