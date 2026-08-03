package com.erfangholami.solidshare.presentation.contacts

import androidx.compose.runtime.Composable
import com.erfangholami.solidshare.presentation.sharing.ReceiverPickerContributor
import javax.inject.Inject

class ContactReceiverPickerContributor @Inject constructor() : ReceiverPickerContributor {

    @Composable
    override fun Picker(onPick: (String) -> Unit, onDismiss: () -> Unit) {
        ContactReceiverPicker(onPick = onPick, onDismiss = onDismiss)
    }
}
