package com.erfangholami.solidshare.presentation.sharing

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.erfangholami.solidshare.R
import com.erfangholami.solidshare.presentation.components.PreviewSamples
import com.erfangholami.solidshare.presentation.theme.AppTheme
import com.erfangholami.solidshare.presentation.util.copyText
import com.erfangholami.solidshare.presentation.util.generateQrBitmap
import com.erfangholami.solidshare.presentation.util.rememberQrLogo
import com.erfangholami.solidshare.util.saveImageToGallery
import kotlinx.coroutines.launch

/**
 * The share-created / share-link result sheet: a scannable QR (with the app logo) for the resource.
 * When [showPublicOption] is true (a public "anyone with the link" share) the user can switch
 * between the in-app deep link and the public browser link via the top tabs; for a WebID share only
 * the Solid Share (in-app) link is shown.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareLinkPanel(
    resourceUri: String,
    deepLink: String,
    bareUrl: String,
    showPublicOption: Boolean = true,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val shareLinkChooserTitle = stringResource(R.string.share_link_chooser_title)
    val imageSavedMsg = stringResource(R.string.image_saved)
    val imageSaveFailedMsg = stringResource(R.string.image_save_failed)

    var showPublic by rememberSaveable { mutableStateOf(false) }
    val usePublic = showPublicOption && showPublic
    val payload = if (usePublic) bareUrl else deepLink
    val logo = rememberQrLogo()
    val bitmap = remember(payload, logo) { generateQrBitmap(payload, 720, logo = logo) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ResourceHeaderRow(resourceUri = resourceUri, subtitle = null)
        HorizontalDivider()

        if (showPublicOption) {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = !showPublic,
                    onClick = { showPublic = false },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    icon = {
                        Image(
                            painter = painterResource(R.drawable.logo),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                    label = { Text(stringResource(R.string.share_link_solid_tab)) },
                )
                SegmentedButton(
                    selected = showPublic,
                    onClick = { showPublic = true },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    icon = {
                        Icon(
                            imageVector = Icons.Filled.Public,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                    label = { Text(stringResource(R.string.share_link_public_tab)) },
                )
            }
        }

        Box(
            modifier = Modifier
                .size(260.dp)
                .background(androidx.compose.ui.graphics.Color.White, RoundedCornerShape(16.dp))
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant,
                    RoundedCornerShape(16.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = stringResource(R.string.qr_code_content_description),
                modifier = Modifier.size(224.dp),
            )
        }

        Text(
            text = stringResource(
                if (usePublic) R.string.share_link_public_title else R.string.share_link_app_title,
            ),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = stringResource(
                if (usePublic) R.string.share_link_public_desc else R.string.share_link_app_desc,
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    MaterialTheme.colorScheme.surfaceContainerHighest,
                    RoundedCornerShape(12.dp),
                )
                .padding(start = 14.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Link,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = payload.removePrefix("https://").removePrefix("http://"),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(R.string.copy_link),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable {
                    scope.launch { clipboard.copyText(payload) }
                },
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
                shape = CircleShape,
                onClick = {
                    val saved = saveImageToGallery(context, bitmap, displayNameForUri(resourceUri))
                    Toast.makeText(
                        context,
                        if (saved) imageSavedMsg else imageSaveFailedMsg,
                        Toast.LENGTH_SHORT,
                    ).show()
                },
            ) {
                Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.save_image))
            }
            Button(
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
                shape = CircleShape,
                onClick = {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, payload)
                    }
                    context.startActivity(Intent.createChooser(intent, shareLinkChooserTitle))
                },
            ) {
                Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.share_link_button))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(showBackground = true, widthDp = 360)
@Composable
private fun ShareLinkPanelPreview() {
    AppTheme {
        ShareLinkPanel(
            resourceUri = PreviewSamples.RESOURCE,
            deepLink = "https://solidshare.app/s?resource=https://alice.solidcommunity.net/photos/trip.jpg",
            bareUrl = PreviewSamples.RESOURCE,
            showPublicOption = true,
        )
    }
}

