package com.erfangholami.solidshare.data.passimport

import java.io.ByteArrayInputStream
import java.util.Locale
import java.util.zip.ZipInputStream

class PassImages(
    val logo: ByteArray? = null,
    val icon: ByteArray? = null,
    val strip: ByteArray? = null,
    val thumbnail: ByteArray? = null,
    val footer: ByteArray? = null,
    val background: ByteArray? = null,
) {
    val isEmpty: Boolean
        get() = logo == null && icon == null && strip == null &&
            thumbnail == null && footer == null && background == null
}

object PkpassImages {

    private const val MAX_IMAGE_BYTES = 4 * 1024 * 1024
    private val WANTED = listOf(
        "logo", "strip", "thumbnail", "icon", "footer", "background",
        "artwork", "primaryLogo", "secondaryLogo",
    )
    private val SCALES = listOf("@3x", "@2x", "")

    fun extract(
        bytes: ByteArray,
        language: String = Locale.getDefault().language,
    ): PassImages? {
        val entries = runCatching {
            ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
                generateSequence { zip.nextEntry }
                    .filter { entry ->
                        !entry.isDirectory &&
                            entry.name.endsWith(".png") &&
                            entry.name.count { it == '/' } <= 1 &&
                            WANTED.any { entry.name.substringAfterLast('/').startsWith(it) }
                    }
                    .associate { entry -> entry.name to zip.readBytes() }
            }
        }.getOrNull() ?: return null

        val languages = listOf("${language.lowercase()}.lproj/", "en.lproj/")
        fun pick(base: String): ByteArray? = SCALES
            .firstNotNullOfOrNull { scale ->
                languages.firstNotNullOfOrNull { prefix -> entries["$prefix$base$scale.png"] }
                    ?: entries["$base$scale.png"]
            }
            ?.takeIf { it.size in 1..MAX_IMAGE_BYTES }

        return PassImages(
            logo = pick("logo") ?: pick("primaryLogo") ?: pick("secondaryLogo") ?: pick("icon"),
            icon = pick("icon"),
            strip = pick("strip"),
            thumbnail = pick("thumbnail"),
            footer = pick("footer"),
            background = pick("background") ?: pick("artwork"),
        ).takeIf { !it.isEmpty }
    }

    fun forTicketFile(bytes: ByteArray): PassImages? = when (TicketFileSniffer.detect(bytes)) {
        TicketFileType.PKPASS -> extract(bytes)
        TicketFileType.PKPASSES -> TicketFileSniffer.firstPassOfBundle(bytes)?.let(::extract)
        else -> null
    }
}
