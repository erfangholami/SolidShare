package com.erfangholami.solidshare.data.passimport

import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

class PassImages(
    val logo: ByteArray? = null,
    val strip: ByteArray? = null,
    val thumbnail: ByteArray? = null,
) {
    val isEmpty: Boolean get() = logo == null && strip == null && thumbnail == null
}

object PkpassImages {

    private const val MAX_IMAGE_BYTES = 4 * 1024 * 1024
    private val WANTED = listOf("logo", "strip", "thumbnail", "icon")
    private val SCALES = listOf("@3x", "@2x", "")

    fun extract(bytes: ByteArray): PassImages? {
        val entries = runCatching {
            ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
                generateSequence { zip.nextEntry }
                    .filter { entry ->
                        !entry.isDirectory &&
                            WANTED.any { entry.name.startsWith(it) } &&
                            entry.name.endsWith(".png") &&
                            entry.name.count { it == '/' } == 0
                    }
                    .associate { entry -> entry.name to zip.readBytes() }
            }
        }.getOrNull() ?: return null

        fun pick(base: String): ByteArray? = SCALES
            .firstNotNullOfOrNull { scale -> entries["$base$scale.png"] }
            ?.takeIf { it.size in 1..MAX_IMAGE_BYTES }

        return PassImages(
            logo = pick("logo") ?: pick("icon"),
            strip = pick("strip"),
            thumbnail = pick("thumbnail"),
        ).takeIf { !it.isEmpty }
    }

    fun forTicketFile(bytes: ByteArray): PassImages? = when (TicketFileSniffer.detect(bytes)) {
        TicketFileType.PKPASS -> extract(bytes)
        TicketFileType.PKPASSES -> TicketFileSniffer.firstPassOfBundle(bytes)?.let(::extract)
        else -> null
    }
}
