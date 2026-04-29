package com.erfangholami.solidshare.domain.model

import java.io.File

data class DownloadedFile(
    val file: File,
    val mimeType: String,
)
