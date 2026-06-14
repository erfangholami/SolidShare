package com.erfangholami.solidshare.presentation.container

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.erfangholami.solidshare.R
import com.erfangholami.solidshare.presentation.theme.AppTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddResourceSheet(
    onDismiss: () -> Unit,
    onUploadFile: () -> Unit,
    onTakePhoto: () -> Unit,
    onRecordVideo: () -> Unit,
    onChooseFromGallery: () -> Unit,
    onCreateFolder: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
    ) {
        Column(modifier = Modifier.padding(bottom = 24.dp)) {
            Text(
                text = stringResource(R.string.add_resource),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            )

            MediaRow(
                icon = Icons.Filled.Upload,
                label = stringResource(R.string.upload_file),
                onClick = {
                    onDismiss()
                    onUploadFile()
                },
            )

            MediaRow(
                icon = Icons.Filled.CameraAlt,
                label = stringResource(R.string.take_photo),
                onClick = {
                    onDismiss()
                    onTakePhoto()
                },
            )

            MediaRow(
                icon = Icons.Filled.Videocam,
                label = stringResource(R.string.record_video),
                onClick = {
                    onDismiss()
                    onRecordVideo()
                },
            )

            MediaRow(
                icon = Icons.Filled.Photo,
                label = stringResource(R.string.choose_from_gallery),
                onClick = {
                    onDismiss()
                    onChooseFromGallery()
                },
            )

            MediaRow(
                icon = Icons.Filled.CreateNewFolder,
                label = stringResource(R.string.create_new_folder),
                onClick = {
                    onDismiss()
                    onCreateFolder()
                },
            )
        }
    }
}

@Composable
private fun MediaRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.width(16.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun MediaRowPreview() {
    AppTheme {
        Surface {
            MediaRow(icon = Icons.Filled.Upload, label = "Upload file", onClick = {})
        }
    }
}
