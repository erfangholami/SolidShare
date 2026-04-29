package com.erfangholami.solidshare.domain.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.erfangholami.solidshare.presentation.theme.solidShareColors

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
) {
    fun getItemSubtitle(): String {
        if (this.isContainer) return "Folder"
        return when {
            this.mimeType != null -> formatMimeType(this.mimeType)
            this.extension != null -> "${this.extension.uppercase()} File"
            else -> "File"
        }
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
    OTHERS;

    val icon: ImageVector
        @Composable
        @ReadOnlyComposable
        get() {
            return when (this) {
                FOLDER -> Icons.Filled.Folder
                IMAGE -> Icons.Filled.Image
                VIDEO -> Icons.Filled.Videocam
                AUDIO -> Icons.Filled.AudioFile
                PDF -> Icons.Filled.PictureAsPdf
                SPREADSHEET -> Icons.Filled.TableChart
                PRESENTATION -> Icons.Filled.Slideshow
                DOCUMENT -> Icons.Filled.Description
                ZIP -> Icons.Filled.FolderZip
                CODE -> Icons.Filled.Code
                OTHERS -> Icons.AutoMirrored.Filled.InsertDriveFile
            }
        }
    val tint: Color
        @Composable
        @ReadOnlyComposable
        get() {
            return when (this) {
                FOLDER -> MaterialTheme.solidShareColors.folder
                IMAGE -> MaterialTheme.solidShareColors.image
                VIDEO -> MaterialTheme.solidShareColors.video
                AUDIO -> MaterialTheme.solidShareColors.audio
                PDF -> MaterialTheme.solidShareColors.pdf
                SPREADSHEET -> MaterialTheme.solidShareColors.spreadsheet
                PRESENTATION -> MaterialTheme.solidShareColors.presentation
                DOCUMENT -> MaterialTheme.solidShareColors.doc
                ZIP -> MaterialTheme.solidShareColors.archive
                CODE -> MaterialTheme.solidShareColors.code
                OTHERS -> MaterialTheme.solidShareColors.file
            }
        }
}

fun getResourceType(isContainer: Boolean, mimeType: String?, extension: String?): ResourceType {
    if (isContainer) return ResourceType.FOLDER
    return when {
        mimeType?.startsWith("image/") == true -> ResourceType.IMAGE
        mimeType?.startsWith("video/") == true -> ResourceType.VIDEO
        mimeType?.startsWith("audio/") == true -> ResourceType.AUDIO
        mimeType == "application/pdf" -> ResourceType.PDF
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
