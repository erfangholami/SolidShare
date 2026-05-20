package com.erfangholami.solidshare.presentation.container

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
import com.erfangholami.solidshare.domain.model.ResourceType
import com.erfangholami.solidshare.presentation.theme.solidShareColors

val ResourceType.icon: ImageVector
    @Composable
    @ReadOnlyComposable
    get() = when (this) {
        ResourceType.FOLDER -> Icons.Filled.Folder
        ResourceType.IMAGE -> Icons.Filled.Image
        ResourceType.VIDEO -> Icons.Filled.Videocam
        ResourceType.AUDIO -> Icons.Filled.AudioFile
        ResourceType.PDF -> Icons.Filled.PictureAsPdf
        ResourceType.SPREADSHEET -> Icons.Filled.TableChart
        ResourceType.PRESENTATION -> Icons.Filled.Slideshow
        ResourceType.DOCUMENT -> Icons.Filled.Description
        ResourceType.ZIP -> Icons.Filled.FolderZip
        ResourceType.CODE -> Icons.Filled.Code
        ResourceType.OTHERS -> Icons.AutoMirrored.Filled.InsertDriveFile
    }

val ResourceType.tint: Color
    @Composable
    @ReadOnlyComposable
    get() = when (this) {
        ResourceType.FOLDER -> MaterialTheme.solidShareColors.folder
        ResourceType.IMAGE -> MaterialTheme.solidShareColors.image
        ResourceType.VIDEO -> MaterialTheme.solidShareColors.video
        ResourceType.AUDIO -> MaterialTheme.solidShareColors.audio
        ResourceType.PDF -> MaterialTheme.solidShareColors.pdf
        ResourceType.SPREADSHEET -> MaterialTheme.solidShareColors.spreadsheet
        ResourceType.PRESENTATION -> MaterialTheme.solidShareColors.presentation
        ResourceType.DOCUMENT -> MaterialTheme.solidShareColors.doc
        ResourceType.ZIP -> MaterialTheme.solidShareColors.archive
        ResourceType.CODE -> MaterialTheme.solidShareColors.code
        ResourceType.OTHERS -> MaterialTheme.solidShareColors.file
    }
