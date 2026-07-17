package com.erfangholami.solidshare.data.passimport

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TicketFileSnifferTest {

    private fun zip(vararg entries: Pair<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            entries.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    @Test
    fun `detects a PDF by its magic bytes regardless of extension`() {
        val pdf = "%PDF-1.7\n...trailing...".toByteArray()
        assertEquals(TicketFileType.PDF, TicketFileSniffer.detect(pdf))
    }

    @Test
    fun `detects PNG and JPEG as images`() {
        val png = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 0, 1, 2)
        val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 0)
        assertEquals(TicketFileType.IMAGE, TicketFileSniffer.detect(png))
        assertEquals(TicketFileType.IMAGE, TicketFileSniffer.detect(jpeg))
        assertEquals("image/png", TicketFileSniffer.imageMimeType(png))
        assertEquals("image/jpeg", TicketFileSniffer.imageMimeType(jpeg))
    }

    @Test
    fun `a zip carrying pass_json is a pkpass`() {
        val pkpass = zip("pass.json" to "{}".toByteArray(), "icon.png" to byteArrayOf(1))
        assertEquals(TicketFileType.PKPASS, TicketFileSniffer.detect(pkpass))
    }

    @Test
    fun `a zip of pkpass members is a bundle, and its first member is extracted`() {
        val firstPass = zip("pass.json" to "{\"a\":1}".toByteArray())
        val bundle = zip("one.pkpass" to firstPass, "two.pkpass" to zip("pass.json" to "{}".toByteArray()))
        assertEquals(TicketFileType.PKPASSES, TicketFileSniffer.detect(bundle))
        assertArrayEquals(firstPass, TicketFileSniffer.firstPassOfBundle(bundle))
    }

    @Test
    fun `an unrelated zip and random bytes are unknown`() {
        val plainZip = zip("notes.txt" to "hello".toByteArray())
        assertEquals(TicketFileType.UNKNOWN, TicketFileSniffer.detect(plainZip))
        assertEquals(TicketFileType.UNKNOWN, TicketFileSniffer.detect(byteArrayOf(1, 2, 3, 4, 5)))
        assertEquals(TicketFileType.UNKNOWN, TicketFileSniffer.detect(ByteArray(0)))
    }

    @Test
    fun `firstPassOfBundle returns null when there is no pkpass member`() {
        assertNull(TicketFileSniffer.firstPassOfBundle(zip("notes.txt" to "x".toByteArray())))
    }

    @Test
    fun `enumerates every pass of a pkpasses bundle in order`() {
        val out = java.io.ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(out).use { zip ->
            listOf("leg1.pkpass" to byteArrayOf(1), "leg2.pkpass" to byteArrayOf(2, 2), "readme.txt" to byteArrayOf(9)).forEach { (name, bytes) ->
                zip.putNextEntry(java.util.zip.ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        val passes = TicketFileSniffer.allPassesOfBundle(out.toByteArray())
        org.junit.Assert.assertEquals(2, passes.size)
        org.junit.Assert.assertEquals(1, passes[0].size)
        org.junit.Assert.assertEquals(2, passes[1].size)
    }
}
