package com.erfangholami.solidshare.presentation.sharing

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.erfangholami.solidshare.R
import com.erfangholami.solidshare.domain.model.PublicProfile
import com.erfangholami.solidshare.presentation.components.ProfileHeader
import com.erfangholami.solidshare.presentation.navigation.ScanQrRoute
import com.erfangholami.solidshare.presentation.util.generateQrBitmap
import com.erfangholami.solidshare.presentation.util.generateQrWithCaptionBitmap
import com.erfangholami.solidshare.presentation.util.loadQrLogo
import com.erfangholami.solidshare.presentation.util.rememberQrLogo
import kotlinx.coroutines.launch
import java.io.OutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareProfilePage(
    navController: NavController,
    viewModel: ShareProfileViewModel,
) {
    val profile by viewModel.activeProfile.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.share_my_profile),
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = stringResource(R.string.close)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { navController.navigate(ScanQrRoute) }) {
                        Icon(
                            Icons.Outlined.QrCodeScanner,
                            contentDescription = stringResource(R.string.scan_qr),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            val p = profile
            if (p == null) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else {
                ShareProfileContent(
                    profile = p,
                    context = context,
                    onMessage = { msg -> scope.launch { snackbarHostState.showSnackbar(msg) } },
                )
            }
        }
    }
}

@Composable
private fun ShareProfileContent(
    profile: PublicProfile,
    context: Context,
    onMessage: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ProfileHeader(profile = profile, avatarSizeDp = 80)

        Spacer(Modifier.height(20.dp))

        QrCard(content = profile.profileDocumentUrl)

        Spacer(Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.share_profile_scan_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.weight(1f))

        ActionRow(
            onShare = { sharePlainText(context, profile.profileDocumentUrl) },
            onCopy = {
                copyToClipboard(context, profile.profileDocumentUrl)
                onMessage(context.getString(R.string.link_copied))
            },
            onDownload = {
                val saved =
                    saveQrToGallery(context, profile.profileDocumentUrl, profile.displayName)
                onMessage(
                    context.getString(
                        if (saved) R.string.qr_saved_to_gallery else R.string.qr_save_failed,
                    ),
                )
            },
        )

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun QrCard(content: String) {
    val logo = rememberQrLogo()
    val qrBitmap = remember(content, logo) { generateQrBitmap(content, 720, logo = logo) }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Box(
            modifier = Modifier.padding(20.dp),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                bitmap = qrBitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier
                    .size(220.dp)
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White),
            )
        }
    }
}

@Composable
private fun ActionRow(
    onShare: () -> Unit,
    onCopy: () -> Unit,
    onDownload: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        ActionButton(Icons.Outlined.Share, stringResource(R.string.share), onShare)
        ActionButton(Icons.Outlined.ContentCopy, stringResource(R.string.copy_link), onCopy)
        ActionButton(Icons.Outlined.Download, stringResource(R.string.download), onDownload)
    }
}

@Composable
private fun ActionButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        onClick = onClick,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

private fun sharePlainText(context: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
        putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.profile_link))
    }
    val chooser = Intent.createChooser(send, context.getString(R.string.share_via))
        .apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
    context.startActivity(chooser)
}

private fun copyToClipboard(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    cm?.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.profile_link), text))
}

private fun saveQrToGallery(context: Context, content: String, displayName: String): Boolean {
    val bitmap = generateQrWithCaptionBitmap(
        content = content,
        caption = content,
        qrSizePx = 1024,
        logo = loadQrLogo(context, R.drawable.logo),
    )
    val fileName =
        "solidshare-${sanitizeForFileName(displayName)}-${System.currentTimeMillis()}.png"
    return saveImageToPictures(context, bitmap, fileName, subfolder = "SolidShare")
}

private fun sanitizeForFileName(name: String): String =
    name.filter { it.isLetterOrDigit() }.take(24).ifEmpty { "profile" }

private fun saveImageToPictures(
    context: Context,
    bitmap: Bitmap,
    fileName: String,
    subfolder: String,
): Boolean {
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(
                    MediaStore.Images.Media.RELATIVE_PATH,
                    "${Environment.DIRECTORY_PICTURES}/$subfolder",
                )
            }
            val uri: Uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: return false
            resolver.openOutputStream(uri)?.use { os: OutputStream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, os)
            } ?: return false
            true
        } else {
            @Suppress("DEPRECATION")
            val picturesDir = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES,
            )
            val targetDir = java.io.File(picturesDir, subfolder).apply { mkdirs() }
            val outFile = java.io.File(targetDir, fileName)
            outFile.outputStream().use { os ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, os)
            }
            android.media.MediaScannerConnection.scanFile(
                context, arrayOf(outFile.absolutePath), arrayOf("image/png"), null,
            )
            true
        }
    } catch (_: Exception) {
        false
    }
}
