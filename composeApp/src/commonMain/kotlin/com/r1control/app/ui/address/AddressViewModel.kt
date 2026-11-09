package com.r1control.app.ui.address

import com.r1control.app.domain.model.AddressInput
import com.r1control.app.domain.model.R1Address
import com.r1control.app.domain.repository.SpeakerException
import com.r1control.app.domain.usecase.SaveAddress
import com.r1control.app.domain.usecase.SaveAddressOutcome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface AddressStatus {
    data object Idle : AddressStatus
    data object Checking : AddressStatus
    data class Invalid(val reason: AddressInput.Reason) : AddressStatus
    data class Unreachable(val address: R1Address, val error: SpeakerException) : AddressStatus
    data class Saved(val address: R1Address) : AddressStatus
}

/**
 * The speaker's address field, shared by the first-run screen and Settings >
 * Advanced. [onSaved] runs once the R1 has answered at the typed address and
 * it is stored.
 */
class AddressViewModel(
    initialText: String,
    private val saveAddress: SaveAddress,
    private val scope: CoroutineScope,
    private val onSaved: (R1Address) -> Unit,
) {
    private val textFlow = MutableStateFlow(initialText)
    private val statusFlow = MutableStateFlow<AddressStatus>(AddressStatus.Idle)
    private var checking: Job? = null

    val text: StateFlow<String> = textFlow.asStateFlow()
    val status: StateFlow<AddressStatus> = statusFlow.asStateFlow()

    fun onText(value: String) {
        textFlow.value = value
        if (statusFlow.value !is AddressStatus.Checking) statusFlow.value = AddressStatus.Idle
    }

    /**
     * The app is in front again. iOS asks for Local Network access at the first
     * check, which fails while the question is on screen, and answering it hands
     * the app back: a check that reached nothing runs again then, as it does
     * after a trip to Settings.
     */
    fun onReturned() {
        val shown = statusFlow.value
        if (shown is AddressStatus.Unreachable && shown.error is SpeakerException.Unreachable) save()
    }

    fun save() {
        if (statusFlow.value is AddressStatus.Checking) return
        statusFlow.value = AddressStatus.Checking
        checking?.cancel()
        checking = scope.launch {
            when (val outcome = saveAddress(textFlow.value)) {
                is SaveAddressOutcome.Invalid -> statusFlow.value = AddressStatus.Invalid(outcome.reason)
                is SaveAddressOutcome.Unreachable -> statusFlow.value = AddressStatus.Unreachable(outcome.address, outcome.error)
                is SaveAddressOutcome.Saved -> {
                    textFlow.value = outcome.address.toString()
                    statusFlow.value = AddressStatus.Saved(outcome.address)
                    onSaved(outcome.address)
                }
            }
        }
    }
}
