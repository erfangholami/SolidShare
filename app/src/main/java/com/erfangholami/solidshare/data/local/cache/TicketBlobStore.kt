package com.erfangholami.solidshare.data.local.cache

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TicketBlobStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val keyManager: CacheKeyManager,
) {

    suspend fun write(webId: String, ticketUri: String, name: String, bytes: ByteArray) {
        withContext(Dispatchers.IO) {
            val file = fileFor(webId, ticketUri, name)
            file.parentFile?.mkdirs()
            keyManager.encryptStream(ByteArrayInputStream(bytes), file)
        }
    }

    suspend fun read(webId: String, ticketUri: String, name: String): ByteArray? =
        withContext(Dispatchers.IO) {
            val file = fileFor(webId, ticketUri, name)
            if (!file.exists()) return@withContext null
            runCatching {
                val out = ByteArrayOutputStream()
                keyManager.decryptStream(file, out)
                out.toByteArray()
            }.getOrElse {
                file.delete()
                null
            }
        }

    suspend fun writeText(webId: String, ticketUri: String, name: String, value: String) {
        write(webId, ticketUri, name, value.toByteArray(Charsets.UTF_8))
    }

    suspend fun readText(webId: String, ticketUri: String, name: String): String? =
        read(webId, ticketUri, name)?.toString(Charsets.UTF_8)

    suspend fun has(webId: String, ticketUri: String, name: String): Boolean =
        withContext(Dispatchers.IO) { fileFor(webId, ticketUri, name).exists() }

    suspend fun move(webId: String, fromTicketUri: String, toTicketUri: String) {
        withContext(Dispatchers.IO) {
            val from = ticketDir(webId, fromTicketUri)
            if (!from.exists()) return@withContext
            val to = ticketDir(webId, toTicketUri)
            to.parentFile?.mkdirs()
            if (!from.renameTo(to)) {
                from.copyRecursively(to, overwrite = true)
                from.deleteRecursively()
            }
        }
    }

    suspend fun deleteTicket(webId: String, ticketUri: String) {
        withContext(Dispatchers.IO) { ticketDir(webId, ticketUri).deleteRecursively() }
    }

    suspend fun clearForWebId(webId: String) {
        withContext(Dispatchers.IO) { File(rootDir(), hash(webId)).deleteRecursively() }
    }

    private fun fileFor(webId: String, ticketUri: String, name: String): File =
        File(ticketDir(webId, ticketUri), name)

    private fun ticketDir(webId: String, ticketUri: String): File =
        File(File(rootDir(), hash(webId)), hash(ticketUri))

    private fun rootDir(): File = File(context.filesDir, BLOB_DIR)

    private fun hash(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
            .take(24)

    companion object {
        private const val BLOB_DIR = "ticket_blobs"
        const val ARTIFACT = "artifact"
        const val ARTIFACT_MIME = "artifact.mime"
        const val REFRESH_SINCE = "refresh.since"
        const val LOGO = "logo"
        const val ICON = "icon"
        const val STRIP = "strip"
        const val THUMBNAIL = "thumbnail"
        const val FOOTER = "footer"
        const val BACKGROUND = "background"
    }
}
