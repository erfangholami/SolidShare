package com.erfangholami.solidshare.presentation.wallet

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CardMembership
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.DirectionsBoat
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Train
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.erfangholami.solidshare.R
import com.erfangholami.solidshare.data.passimport.normalizePassDateTime
import com.erfangholami.solidshare.domain.model.TicketBarcodeFormat
import com.erfangholami.solidshare.domain.model.Ticket
import com.erfangholami.solidshare.domain.model.TicketCategory
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

fun iconFor(category: TicketCategory): ImageVector = when (category) {
    TicketCategory.EVENT -> Icons.Filled.ConfirmationNumber
    TicketCategory.FLIGHT -> Icons.Filled.Flight
    TicketCategory.TRAIN -> Icons.Filled.Train
    TicketCategory.BUS -> Icons.Filled.DirectionsBus
    TicketCategory.BOAT -> Icons.Filled.DirectionsBoat
    TicketCategory.CINEMA -> Icons.Filled.Movie
    TicketCategory.LOYALTY -> Icons.Filled.CardMembership
    TicketCategory.COUPON -> Icons.Filled.LocalOffer
    TicketCategory.GENERIC -> Icons.Filled.AccountBalanceWallet
}

@Composable
fun labelFor(category: TicketCategory): String = stringResource(
    when (category) {
        TicketCategory.EVENT -> R.string.ticket_category_event
        TicketCategory.FLIGHT -> R.string.ticket_category_flight
        TicketCategory.TRAIN -> R.string.ticket_category_train
        TicketCategory.BUS -> R.string.ticket_category_bus
        TicketCategory.BOAT -> R.string.ticket_category_boat
        TicketCategory.CINEMA -> R.string.ticket_category_cinema
        TicketCategory.LOYALTY -> R.string.ticket_category_loyalty
        TicketCategory.COUPON -> R.string.ticket_category_coupon
        TicketCategory.GENERIC -> R.string.ticket_category_generic
    },
)

@Composable
fun TicketCategoryIcon(
    category: TicketCategory,
    size: Dp = 48.dp,
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = iconFor(category),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(size / 2),
        )
    }
}

fun formatTicketDate(raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    val iso = normalizePassDateTime(raw)
    return runCatching {
        if (iso.contains('T')) {
            val instant = runCatching { Instant.parse(iso) }
                .getOrElse {
                    runCatching { OffsetDateTime.parse(iso).toInstant() }
                        .getOrElse { LocalDateTime.parse(iso).atZone(ZoneId.systemDefault()).toInstant() }
                }
            DateTimeFormatter.ofPattern("EEE d MMM yyyy · HH:mm", Locale.getDefault())
                .withZone(ZoneId.systemDefault())
                .format(instant)
        } else {
            val date = LocalDate.parse(iso)
            DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.getDefault()).format(date)
        }
    }.getOrDefault(iso)
}

fun isTicketExpired(ticket: Ticket, now: Instant = Instant.now()): Boolean {
    if (ticket.voided == true) return true
    ticketInstantOrNull(ticket.validThrough)?.let { ends ->
        if (ends.isBefore(now)) return true
    }
    val reference = ticketInstantOrNull(ticket.event?.start ?: ticket.journey?.from?.time)
        ?: return false
    return reference.isBefore(now)
}

fun ticketInstantOrNull(raw: String?): Instant? {
    if (raw.isNullOrBlank()) return null
    val iso = normalizePassDateTime(raw)
    return runCatching { Instant.parse(iso) }.getOrElse {
        runCatching { OffsetDateTime.parse(iso).toInstant() }.getOrElse {
            runCatching {
                LocalDateTime.parse(iso).atZone(ZoneId.systemDefault()).toInstant()
            }.getOrElse {
                runCatching {
                    LocalDate.parse(iso).atStartOfDay(ZoneId.systemDefault()).toInstant()
                }.getOrNull()
            }
        }
    }
}
