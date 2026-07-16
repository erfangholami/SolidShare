package com.erfangholami.solidshare.presentation.wallet

import com.erfangholami.solidshare.data.passimport.BcbpParser
import com.erfangholami.solidshare.data.passimport.TicketScanFormats
import com.erfangholami.solidshare.data.passimport.mlKitFormatToDomain

import android.Manifest
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.ViewModel
import androidx.navigation.NavController
import com.erfangholami.solidshare.R
import com.erfangholami.solidshare.data.repo.tickets.TicketsRepository
import com.erfangholami.solidshare.domain.model.TicketDraft
import com.erfangholami.solidshare.domain.model.TicketSource
import com.erfangholami.solidshare.presentation.navigation.TicketEditRoute
import com.erfangholami.solidshare.presentation.navigation.TicketScanRoute
import com.erfangholami.solidshare.presentation.permissions.rememberPermissionGate
import com.erfangholami.solidshare.presentation.sharing.ScannerContent
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class TicketScanViewModel @Inject constructor(
    private val ticketsRepository: TicketsRepository,
) : ViewModel() {

    fun draftFrom(raw: String, mlKitFormat: Int): TicketDraft =
        ticketsRepository.parseTicketQr(raw)
            ?: BcbpParser.parse(raw)?.toDraft()?.copy(
                token = raw,
                barcodeFormat = mlKitFormatToDomain(mlKitFormat),
            )
            ?: TicketDraft(
                token = raw.trim(),
                barcodeFormat = mlKitFormatToDomain(mlKitFormat),
                source = TicketSource.SCAN,
            )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TicketScanPage(
    navController: NavController,
    viewModel: TicketScanViewModel,
) {
    val cameraGate = rememberPermissionGate(
        permission = Manifest.permission.CAMERA,
        required = true,
        rationaleTitle = stringResource(R.string.camera_permission_title),
        rationaleText = stringResource(R.string.camera_permission_rationale),
        settingsText = stringResource(R.string.camera_permission_rationale),
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.scan_ticket_title),
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = stringResource(R.string.close),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.surface,
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            ScannerContent(
                subtitle = stringResource(R.string.scan_ticket_subtitle),
                hasPermission = cameraGate.isGranted,
                onRequestPermission = { cameraGate.run {} },
                onResult = { raw, format ->
                    navController.navigate(
                        TicketEditRoute(draft = viewModel.draftFrom(raw, format)),
                    ) {
                        popUpTo(TicketScanRoute) { inclusive = true }
                    }
                },
                barcodeFormats = TicketScanFormats,
                submitLabel = stringResource(R.string.scan_ticket_submit),
            )
        }
    }
}
