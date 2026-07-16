package com.erfangholami.solidshare.presentation.wallet

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.erfangholami.solidshare.R
import com.erfangholami.solidshare.data.passimport.normalizePassKey
import com.erfangholami.solidshare.domain.model.TicketCategory
import com.erfangholami.solidshare.domain.model.TicketExtra

data class NormalizedField(
    val labelRes: Int? = null,
    val fallbackLabel: String = "",
    val valueRes: Int? = null,
    val value: String = "",
)

object PassVocabulary {

    private val LABELS = mapOf(
        "boarding" to R.string.pass_label_boarding,
        "boardingtime" to R.string.pass_label_boarding,
        "boarduntil" to R.string.pass_label_boarding,
        "boardinguntil" to R.string.pass_label_boarding,
        "gatecloses" to R.string.pass_label_gate_closes,
        "gateclose" to R.string.pass_label_gate_closes,
        "gateclosure" to R.string.pass_label_gate_closes,
        "doorscloseat" to R.string.pass_label_gate_closes,
        "doorsclose" to R.string.pass_label_gate_closes,
        "class" to R.string.pass_label_class,
        "cabinclass" to R.string.pass_label_class,
        "travelclass" to R.string.pass_label_class,
        "fareclass" to R.string.pass_label_class,
        "cabin" to R.string.pass_label_class,
        "zone" to R.string.pass_label_group,
        "boardingzone" to R.string.pass_label_group,
        "group" to R.string.pass_label_group,
        "boardinggroup" to R.string.pass_label_group,
        "sequence" to R.string.pass_label_sequence,
        "sequencenumber" to R.string.pass_label_sequence,
        "seq" to R.string.pass_label_sequence,
        "status" to R.string.pass_label_status,
        "frequentflyer" to R.string.pass_label_frequent_flyer,
        "frequentflyernumber" to R.string.pass_label_frequent_flyer,
        "date" to R.string.pass_field_date,
        "traveldate" to R.string.pass_field_date,
        "departuredate" to R.string.pass_field_date,
        "validuntil" to R.string.pass_field_valid_until,
        "expires" to R.string.pass_field_valid_until,
        "expiry" to R.string.pass_field_valid_until,
        "expirydate" to R.string.pass_field_valid_until,
        "door" to R.string.pass_label_door,
        "boardingdoor" to R.string.pass_label_door,
    )

    private val DOOR_VALUES = mapOf(
        "front" to R.string.pass_value_front,
        "back" to R.string.pass_value_rear,
        "rear" to R.string.pass_value_rear,
    )

    private val CABIN_VALUES = mapOf(
        "F" to R.string.pass_value_first,
        "J" to R.string.pass_value_business,
        "C" to R.string.pass_value_business,
        "W" to R.string.pass_value_premium_economy,
        "Y" to R.string.pass_value_economy,
    )

    private val TRAVEL = setOf(
        TicketCategory.FLIGHT,
        TicketCategory.TRAIN,
        TicketCategory.BUS,
        TicketCategory.BOAT,
    )

    fun normalize(category: TicketCategory, rawLabel: String?, rawValue: String): NormalizedField {
        val key = normalizePassKey(rawLabel.orEmpty())

        doorField(key, rawValue)?.let { return it }

        val labelRes = LABELS[key]
        val valueRes = when {
            labelRes == R.string.pass_label_class && category == TicketCategory.FLIGHT ->
                CABIN_VALUES[rawValue.trim().uppercase()]

            labelRes == R.string.pass_label_door || key.startsWith("boardingdoor") ->
                DOOR_VALUES[normalizePassKey(rawValue)]

            else -> null
        }

        return NormalizedField(
            labelRes = labelRes,
            fallbackLabel = rawLabel.orEmpty(),
            valueRes = valueRes,
            value = formatTicketDate(rawValue.takeIf { looksLikeIsoDate(it) }) ?: rawValue,
        )
    }

    private fun doorField(key: String, rawValue: String): NormalizedField? {
        val suffix = listOf("front", "back", "rear").firstOrNull {
            key == "boardingdoor$it" || key == "door$it"
        }
        if (suffix != null) {
            return NormalizedField(
                labelRes = R.string.pass_label_door,
                valueRes = DOOR_VALUES[suffix],
                value = rawValue,
            )
        }
        if (key == "boardingdoor" || key == "door") {
            DOOR_VALUES[normalizePassKey(rawValue)]?.let { valueRes ->
                return NormalizedField(
                    labelRes = R.string.pass_label_door,
                    valueRes = valueRes,
                    value = rawValue,
                )
            }
        }
        return null
    }

    private fun looksLikeIsoDate(value: String): Boolean {
        val trimmed = value.trim()
        return trimmed.length >= 10 &&
            trimmed[4] == '-' && trimmed[7] == '-' &&
            trimmed.take(4).all { it.isDigit() }
    }

    fun isTravel(category: TicketCategory): Boolean = category in TRAVEL
}

@Composable
fun normalizedExtra(category: TicketCategory, extra: TicketExtra): Pair<String, String> {
    val field = PassVocabulary.normalize(category, extra.label, extra.value)
    val label = field.labelRes?.let { stringResource(it) } ?: field.fallbackLabel
    val value = field.valueRes?.let { stringResource(it) } ?: field.value
    return label to value
}
