package com.erfangholami.solidshare.presentation.wallet

import androidx.compose.ui.graphics.Color
import com.erfangholami.solidshare.domain.model.TicketCategory
import com.erfangholami.solidshare.domain.model.TicketStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PassCardLogicTest {

    @Test
    fun `parses pkpass rgb colours`() {
        assertEquals(Color(0, 54, 113), parsePassColor("rgb(0, 54, 113)"))
        assertEquals(Color(255, 255, 255), parsePassColor("rgb(255,255,255)"))
        assertEquals(Color(10, 20, 30), parsePassColor("rgba(10, 20, 30, 0.5)"))
    }

    @Test
    fun `parses hex colours`() {
        assertEquals(Color(0xFFA8C6E8), parsePassColor("#A8C6E8"))
        assertEquals(Color(0x80A8C6E8), parsePassColor("#80A8C6E8"))
        assertEquals(Color(0xFF112233), parsePassColor("#123"))
    }

    @Test
    fun `rejects garbage colours`() {
        assertNull(parsePassColor(null))
        assertNull(parsePassColor(""))
        assertNull(parsePassColor("bleu"))
        assertNull(parsePassColor("#XYZ123"))
    }

    @Test
    fun `custom background wins and gets a readable foreground`() {
        val data = PassCardData(
            title = "T",
            category = TicketCategory.FLIGHT,
            style = TicketStyle(backgroundColor = "rgb(255,255,255)"),
        )
        val palette = passPalette(data)
        assertEquals(Color(255, 255, 255), palette.container)
        assertEquals(bestOn(Color.White), palette.onContainer)
    }

    @Test
    fun `category default is used without a style`() {
        val flight = passPalette(PassCardData(title = "T", category = TicketCategory.FLIGHT))
        val event = passPalette(PassCardData(title = "T", category = TicketCategory.EVENT))
        assertTrue(flight.container != event.container)
        assertEquals(Color.White, flight.onContainer)
    }

    @Test
    fun `expired palette is greyed but keeps text readable`() {
        val data = PassCardData(title = "T", category = TicketCategory.FLIGHT)
        val normal = passPalette(data)
        val expired = passPalette(data, expired = true)
        assertTrue(normal.container != expired.container)
        assertTrue(expired.onContainer.alpha > 0.8f)
    }

    @Test
    fun `barcode caption prefers alt text then the booking reference from a bcbp payload`() {
        assertEquals("1234 5678", barcodeCaption("1234 5678", "anything"))

        val bcbp = buildString {
            append("M1")
            append("GHOLAMI/ERFAN".padEnd(20))
            append("E")
            append("ABC123".padEnd(7))
            append("AMS")
            append("JFK")
            append("KL".padEnd(3))
            append("0641".padEnd(5))
            append("244")
            append("Y")
            append("027A")
            append("00012")
            append("3")
        }
        assertEquals("ABC123", barcodeCaption(null, bcbp))

        assertNull(barcodeCaption(null, "just-a-token"))
        assertNull(barcodeCaption(null, null))
    }
}
