package com.erfangholami.solidshare.data.passimport

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class FetchedLink(val bytes: ByteArray, val fileName: String?)

class TicketLinkFetcher @Inject constructor() {

    suspend fun fetch(url: String): FetchedLink? = withContext(Dispatchers.IO) {
        runCatching { follow(url) }.getOrNull()
    }

    private fun follow(startUrl: String): FetchedLink? {
        var current = startUrl
        repeat(MAX_REDIRECTS + 1) {
            if (!current.startsWith("https://")) return null
            val connection = URL(current).openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = false
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("User-Agent", BROWSER_USER_AGENT)
            connection.setRequestProperty("Accept", "*/*")
            try {
                val code = connection.responseCode
                when (code) {
                    in 300..399 -> {
                        val location = connection.getHeaderField("Location") ?: return null
                        current = URL(URL(current), location).toString()
                    }

                    in 200..299 -> {
                        if (connection.contentLengthLong > MAX_BYTES) return null
                        val bytes = connection.inputStream.use { it.readCapped() } ?: return null
                        return FetchedLink(
                            bytes = bytes,
                            fileName = fileNameFrom(
                                connection.getHeaderField("Content-Disposition"),
                                current,
                            ),
                        )
                    }

                    else -> return null
                }
            } finally {
                connection.disconnect()
            }
        }
        return null
    }

    private fun InputStream.readCapped(): ByteArray? {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val read = read(buffer)
            if (read < 0) return out.toByteArray()
            out.write(buffer, 0, read)
            if (out.size() > MAX_BYTES) return null
        }
    }

    companion object {
        private const val MAX_REDIRECTS = 5
        private const val TIMEOUT_MS = 15_000
        private const val MAX_BYTES = 20 * 1024 * 1024
        private const val BROWSER_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Pixel 5) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/126.0.6478.122 Mobile Safari/537.36"

        private val URL_PATTERN = Regex("""https://\S+""")
        private val FILENAME_PATTERN =
            Regex("""filename\*?=(?:UTF-8'')?"?([^";]+)""", RegexOption.IGNORE_CASE)

        fun firstHttpsUrl(text: String): String? =
            URL_PATTERN.find(text)?.value?.trimEnd('.', ',', ')', ']', '>', '"', '\'', ';')

        internal fun fileNameFrom(contentDisposition: String?, url: String): String? {
            contentDisposition
                ?.let { FILENAME_PATTERN.find(it)?.groupValues?.get(1)?.trim('"', ' ') }
                ?.takeIf { it.isNotBlank() }
                ?.let { return it }
            return url.substringBefore('#').substringBefore('?')
                .substringAfterLast('/')
                .takeIf { it.length > 1 && it.contains('.') }
        }
    }
}
