package com.erfangholami.solidshare.presentation.wallet

import com.erfangholami.solidshare.R
import com.erfangholami.solidshare.domain.model.TicketCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PassVocabularyTest {

    @Test
    fun `boarding_door_front becomes Door with a Front value`() {
        val field = PassVocabulary.normalize(TicketCategory.BUS, "boarding_door_front", "true")
        assertEquals(R.string.pass_label_door, field.labelRes)
        assertEquals(R.string.pass_value_front, field.valueRes)
    }

    @Test
    fun `a door field with a front-back value is translated`() {
        val field = PassVocabulary.normalize(TicketCategory.BUS, "Boarding door", "FRONT")
        assertEquals(R.string.pass_label_door, field.labelRes)
        assertEquals(R.string.pass_value_front, field.valueRes)

        val rear = PassVocabulary.normalize(TicketCategory.BUS, "door", "back")
        assertEquals(R.string.pass_value_rear, rear.valueRes)
    }

    @Test
    fun `issuer jargon labels map to friendly labels`() {
        assertEquals(
            R.string.pass_label_gate_closes,
            PassVocabulary.normalize(TicketCategory.FLIGHT, "gate_closes", "09:25").labelRes,
        )
        assertEquals(
            R.string.pass_label_boarding,
            PassVocabulary.normalize(TicketCategory.FLIGHT, "boarding_time", "09:10").labelRes,
        )
        assertEquals(
            R.string.pass_label_group,
            PassVocabulary.normalize(TicketCategory.FLIGHT, "boarding_group", "B").labelRes,
        )
    }

    @Test
    fun `cabin letters translate to class names on flights only`() {
        val flight = PassVocabulary.normalize(TicketCategory.FLIGHT, "class", "J")
        assertEquals(R.string.pass_label_class, flight.labelRes)
        assertEquals(R.string.pass_value_business, flight.valueRes)

        val train = PassVocabulary.normalize(TicketCategory.TRAIN, "class", "J")
        assertEquals(R.string.pass_label_class, train.labelRes)
        assertNull(train.valueRes)
    }

    @Test
    fun `iso dates in values are rendered human-readable`() {
        val field = PassVocabulary.normalize(
            TicketCategory.EVENT,
            "Doors open",
            "2026-08-14T18:30:00+02:00",
        )
        assertTrue(field.value != "2026-08-14T18:30:00+02:00")
        assertTrue(field.value.contains("2026"))
    }

    @Test
    fun `unknown fields pass through untouched`() {
        val field = PassVocabulary.normalize(TicketCategory.EVENT, "Tour name", "World Tour 2026")
        assertNull(field.labelRes)
        assertEquals("Tour name", field.fallbackLabel)
        assertEquals("World Tour 2026", field.value)
    }
}
