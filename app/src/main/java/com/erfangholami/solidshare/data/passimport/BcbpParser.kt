package com.erfangholami.solidshare.data.passimport

import com.erfangholami.solidshare.domain.model.TicketCategory
import com.erfangholami.solidshare.domain.model.TicketDraft
import com.erfangholami.solidshare.domain.model.TicketEventInfo
import com.erfangholami.solidshare.domain.model.TicketJourney
import com.erfangholami.solidshare.domain.model.TicketSeatInfo
import com.erfangholami.solidshare.domain.model.TicketSource
import com.erfangholami.solidshare.domain.model.TicketStop
import com.erfangholami.solidshare.domain.model.TransportMode
import java.time.LocalDate

object BcbpParser {

    private const val MIN_LENGTH = 58
    private const val GRACE_DAYS = 2L

    fun parse(payload: String, reference: LocalDate = LocalDate.now()): TicketDraft? {
        if (payload.length < MIN_LENGTH || payload[0] != 'M') return null
        payload[1].digitToIntOrNull()?.takeIf { it in 1..9 } ?: return null

        val holder = formatName(payload.substring(2, 22).trim())

        var index = 23
        fun take(width: Int): String = payload.substring(index, index + width).also { index += width }

        val pnr = take(7).trim()
        val from = take(3).trim()
        val to = take(3).trim()
        val carrier = take(3).trim()
        val flightNumber = take(5).trim()
        val julianDay = take(3).trim().toIntOrNull()
        take(1)
        val seat = take(4).trim()

        if (!from.isAirportCode() || !to.isAirportCode()) return null

        val date = julianDay?.let { julianToIsoDate(it, reference) }
        val service = (carrier + flightNumber.trimStart('0')).takeIf { it.isNotBlank() }

        return TicketDraft(
            title = "$from → $to",
            category = TicketCategory.FLIGHT,
            number = pnr.ifBlank { null },
            holder = holder,
            seat = seatOf(seat),
            event = date?.let { TicketEventInfo(start = it) },
            journey = TicketJourney(
                mode = TransportMode.FLIGHT,
                carrier = carrier.ifBlank { null },
                serviceNumber = service,
                from = TicketStop(code = from, time = date),
                to = TicketStop(code = to),
            ),
            source = TicketSource.BCBP,
        )
    }

    private fun String.isAirportCode(): Boolean = length == 3 && all { it in 'A'..'Z' }

    private fun formatName(raw: String): String? {
        if (raw.isBlank()) return null
        val parts = raw.split('/').map { it.trim() }.filter { it.isNotEmpty() }
        val ordered = when (parts.size) {
            0 -> return null
            1 -> parts
            else -> listOf(parts[1], parts[0])
        }
        return ordered.joinToString(" ") { titleCase(it) }
    }

    private fun titleCase(word: String): String =
        word.lowercase().replaceFirstChar { it.uppercase() }

    private fun seatOf(raw: String): TicketSeatInfo? {
        val seat = raw.trimStart('0').ifBlank { null } ?: return null
        return TicketSeatInfo(number = seat)
    }

    private fun julianToIsoDate(dayOfYear: Int, reference: LocalDate): String? {
        if (dayOfYear !in 1..366) return null
        val candidates = (reference.year - 1..reference.year + 1)
            .mapNotNull { year -> runCatching { LocalDate.ofYearDay(year, dayOfYear) }.getOrNull() }
            .sorted()
        val floor = reference.minusDays(GRACE_DAYS)
        return (candidates.firstOrNull { !it.isBefore(floor) } ?: candidates.lastOrNull())?.toString()
    }
}
