package com.erfangholami.solidshare.domain.model

import kotlinx.serialization.Serializable
import java.net.URLDecoder
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.ln
import kotlin.math.pow

@Serializable
data class ContainerItem(
    val identifier: String,
    val isContainer: Boolean,
    val name: String,
    val extension: String?,
    val mimeType: String?,
    val resourceType: ResourceType,
    val resourceTypes: List<String>,
    val sizeBytes: Long?,
    val lastModified: Long?,
    val etag: String?,
    val access: ResourceAccess = ResourceAccess.FULL,
    val createdTime: Long? = null,
) {
    fun getItemSubtitle(): String {
        if (isContainer) {
            val size = sizeLabel()
            val date = lastModified?.let(::formatLastModified)
            return listOfNotNull("Folder", size, date).joinToString(" · ")
        }
        val type = when {
            mimeType != null -> formatMimeType(mimeType)
            extension != null -> "${extension.uppercase()} File"
            else -> "File"
        }
        val size = sizeLabel()
        val date = lastModified?.let(::formatLastModified)
        return listOfNotNull(type, size, date).joinToString(" · ")
    }

    fun typeLabel(): String = when {
        isContainer -> "Folder"
        mimeType != null -> formatMimeType(mimeType)
        extension != null -> "${extension.uppercase()} File"
        else -> "File"
    }

    fun shortTypeLabel(): String = when (resourceType) {
        ResourceType.FOLDER -> "Folder"
        ResourceType.IMAGE -> "Image"
        ResourceType.VIDEO -> "Video"
        ResourceType.AUDIO -> "Audio"
        ResourceType.PDF -> "PDF"
        ResourceType.SPREADSHEET -> "Sheet"
        ResourceType.PRESENTATION -> "Slides"
        ResourceType.DOCUMENT -> "Doc"
        ResourceType.ZIP -> "Archive"
        ResourceType.CODE -> "Code"
        ResourceType.OTHERS -> "File"
    }

    fun sizeLabel(): String? = sizeBytes?.takeIf { it > 0 }?.let(::formatFileSize)

    fun modifiedLabel(): String? = lastModified?.let(::formatFullTimestamp)

    fun createdLabel(): String? = createdTime?.let(::formatFullTimestamp)

    fun parentContainerName(): String? {
        val withoutLast = identifier.trimEnd('/').substringBeforeLast('/', "")
        if (withoutLast.isBlank()) return null
        val tail = withoutLast.substringAfterLast('/')
        val decoded =
            runCatching { URLDecoder.decode(tail, Charsets.UTF_8.name()) }.getOrDefault(tail)
        return decoded.ifBlank { null }
    }
}

enum class ResourceType {
    FOLDER,
    IMAGE,
    VIDEO,
    AUDIO,
    PDF,
    SPREADSHEET,
    PRESENTATION,
    DOCUMENT,
    ZIP,
    CODE,
    OTHERS,
}

fun getResourceType(isContainer: Boolean, mimeType: String?, extension: String?): ResourceType {
    if (isContainer) return ResourceType.FOLDER
    return when {
        mimeType?.startsWith("image/") == true ||
                extension?.let {
                    it == "jpg" || it == "jpeg" || it == "png" || it == "gif" || it == "webp" ||
                            it == "bmp" || it == "heic" || it == "heif" || it == "svg" ||
                            it == "tif" || it == "tiff" || it == "avif" || it == "ico"
                } == true -> ResourceType.IMAGE

        mimeType?.startsWith("video/") == true ||
                extension?.let {
                    it == "mp4" || it == "mov" || it == "avi" || it == "mkv" || it == "webm" ||
                            it == "m4v" || it == "3gp" || it == "mpeg" || it == "mpg" ||
                            it == "wmv" || it == "flv"
                } == true -> ResourceType.VIDEO

        mimeType?.startsWith("audio/") == true ||
                extension?.let {
                    it == "mp3" || it == "wav" || it == "ogg" || it == "m4a" || it == "aac" ||
                            it == "flac" || it == "opus" || it == "wma" || it == "aiff" ||
                            it == "mid" || it == "midi"
                } == true -> ResourceType.AUDIO

        mimeType == "application/pdf" || extension == "pdf" -> ResourceType.PDF
        mimeType?.contains("spreadsheet") == true || mimeType?.contains("excel") == true ||
                extension?.let { it == "xls" || it == "xlsx" || it == "csv" || it == "ods" } == true -> ResourceType.SPREADSHEET

        mimeType?.contains("presentation") == true || mimeType?.contains("powerpoint") == true ||
                extension?.let { it == "ppt" || it == "pptx" || it == "odp" } == true -> ResourceType.PRESENTATION

        mimeType?.startsWith("text/") == true || mimeType?.contains("word") == true ||
                mimeType?.contains("document") == true ||
                extension?.let { it == "doc" || it == "docx" || it == "odt" || it == "txt" || it == "md" } == true -> ResourceType.DOCUMENT

        mimeType?.contains("zip") == true || mimeType?.contains("tar") == true ||
                mimeType?.contains("gzip") == true || mimeType?.contains("7z") == true ||
                extension?.let { it == "zip" || it == "tar" || it == "gz" || it == "7z" || it == "rar" } == true -> ResourceType.ZIP

        extension?.let {
            it == "kt" || it == "java" || it == "py" || it == "js" || it == "ts" ||
                    it == "html" || it == "css" || it == "xml" || it == "json" || it == "sh" ||
                    it == "rb" || it == "go" || it == "rs" || it == "swift" || it == "c" || it == "cpp"
        } == true || mimeType?.contains("json") == true -> ResourceType.CODE

        else -> ResourceType.OTHERS
    }
}

private fun formatFileSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    val exp = (ln(bytes.toDouble()) / ln(1024.0)).toInt().coerceIn(1, units.size)
    val value = bytes / 1024.0.pow(exp.toDouble())
    return String.format(Locale.getDefault(), "%.1f %s", value, units[exp - 1])
}

private val sameYearFormatter = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())
private val withYearFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault())
private val fullTimestampFormatter =
    DateTimeFormatter.ofPattern("MMM d, yyyy, HH:mm", Locale.getDefault())

private fun formatLastModified(epochMillis: Long): String {
    val date = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate()
    val formatter = if (date.year == LocalDate.now().year) sameYearFormatter else withYearFormatter
    return date.format(formatter)
}

private fun formatFullTimestamp(epochMillis: Long): String =
    Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).format(fullTimestampFormatter)

private fun formatMimeType(mime: String): String = when {
    mime.startsWith("image/") -> "${mime.substringAfter("image/").uppercase().take(8)} Image"
    mime.startsWith("video/") -> "${mime.substringAfter("video/").uppercase().take(8)} Video"
    mime.startsWith("audio/") -> "${mime.substringAfter("audio/").uppercase().take(8)} Audio"
    mime == "application/pdf" -> "PDF Document"
    mime.startsWith("text/") -> when (mime.substringAfter("text/")) {
        "plain" -> "Text Document"
        "html" -> "HTML Document"
        "css" -> "CSS Stylesheet"
        "javascript" -> "JavaScript File"
        "markdown" -> "Markdown Document"
        else -> "${mime.substringAfter("text/").replaceFirstChar { it.uppercase() }} File"
    }

    mime == "application/json" -> "JSON File"
    mime.contains("zip") -> "ZIP Archive"
    mime.contains("tar") -> "TAR Archive"
    mime.contains("gzip") -> "GZIP Archive"
    mime.contains("word") || mime.contains("document") -> "Word Document"
    mime.contains("sheet") || mime.contains("excel") -> "Spreadsheet"
    mime.contains("presentation") || mime.contains("powerpoint") -> "Presentation"
    else -> {
        val sub = mime.substringAfter("/").replace(Regex("[+;].*"), "")
        if (sub.length in 1..16) "${sub.replaceFirstChar { it.uppercase() }} File" else "File"
    }
}
