package com.erfangholami.solidshare.presentation.sharing

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import javax.inject.Inject
import javax.inject.Singleton

interface ReceiverPickerContributor {

    @Composable
    fun Picker(onPick: (String) -> Unit, onDismiss: () -> Unit)
}

@Singleton
class ReceiverPickerRegistry @Inject constructor(
    private val contributors: Set<@JvmSuppressWildcards ReceiverPickerContributor>,
) {
    fun preferred(): ReceiverPickerContributor? = contributors.firstOrNull()
}

val LocalReceiverPicker = staticCompositionLocalOf<ReceiverPickerContributor?> { null }
