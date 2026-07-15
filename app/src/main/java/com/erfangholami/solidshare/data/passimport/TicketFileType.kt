package com.erfangholami.solidshare.data.passimport

import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

enum class TicketFileType { PKPASS, PKPASSES, PDF, IMAGE, UNKNOWN }

object TicketFileSniffer {

    fun detect(bytes: ByteArray): TicketFileType = when {
        startsWith(bytes, PDF_MAGIC) -> TicketFileType.PDF
        startsWith(bytes, PNG_MAGIC) || startsWith(bytes, JPEG_MAGIC) -> TicketFileType.IMAGE
        startsWith(bytes, ZIP_MAGIC) -> classifyZip(bytes)
        else -> TicketFileType.UNKNOWN
    }

    fun imageMimeType(bytes: ByteArray): String = when {
        startsWith(bytes, PNG_MAGIC) -> "image/png"
        startsWith(bytes, JPEG_MAGIC) -> "image/jpeg"
        else -> "image/*"
    }

    /** Extracts the first `.pkpass` member of a `.pkpasses` bundle, or `null` if there is none. */
    fun firstPassOfBundle(bytes: ByteArray): ByteArray? = runCatching {
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            generateSequence { zip.nextEntry }
                .firstOrNull { it.name.endsWith(".pkpass", ignoreCase = true) }
                ?.let { zip.readBytes() }
        }
    }.getOrNull()

    private fun classifyZip(bytes: ByteArray): TicketFileType {
        val names = zipEntryNames(bytes)
        return when {
            names.any { it == "pass.json" } -> TicketFileType.PKPASS
            names.any { it.endsWith(".pkpass", ignoreCase = true) } -> TicketFileType.PKPASSES
            else -> TicketFileType.UNKNOWN
        }
    }

    private fun zipEntryNames(bytes: ByteArray): List<String> = runCatching {
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            generateSequence { zip.nextEntry }.map { it.name }.toList()
        }
    }.getOrDefault(emptyList())

    private fun startsWith(bytes: ByteArray, magic: ByteArray): Boolean {
        if (bytes.size < magic.size) return false
        for (i in magic.indices) if (bytes[i] != magic[i]) return false
        return true
    }

    private val PDF_MAGIC = byteArrayOf('%'.code.toByte(), 'P'.code.toByte(), 'D'.code.toByte(), 'F'.code.toByte())
    private val PNG_MAGIC = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte())
    private val JPEG_MAGIC = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())
    private val ZIP_MAGIC = byteArrayOf('P'.code.toByte(), 'K'.code.toByte(), 0x03, 0x04)
}
