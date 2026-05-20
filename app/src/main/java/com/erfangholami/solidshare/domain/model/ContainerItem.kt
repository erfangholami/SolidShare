package com.erfangholami.solidshare.domain.model

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
        if (isContainer) return "Folder"
        return when {
            mimeType != null -> formatMimeType(mimeType)
            extension != null -> "${extension.uppercase()} File"
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
    OTHERS,
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
