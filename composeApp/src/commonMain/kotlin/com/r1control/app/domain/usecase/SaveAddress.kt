package com.r1control.app.domain.usecase

import com.r1control.app.domain.model.AddressInput
import com.r1control.app.domain.model.R1Address
import com.r1control.app.domain.repository.PreferencesRepository
import com.r1control.app.domain.repository.SpeakerException
import com.r1control.app.domain.repository.SpeakerRepository
import kotlinx.coroutines.CancellationException

sealed interface SaveAddressOutcome {
    data class Saved(val address: R1Address) : SaveAddressOutcome
    data class Invalid(val reason: AddressInput.Reason) : SaveAddressOutcome
    /** The text was an address but nothing R1-like answered there; nothing was saved. */
    data class Unreachable(val address: R1Address, val error: SpeakerException) : SaveAddressOutcome
}

/** Validates what was typed, checks the speaker answers there, then saves it. */
class SaveAddress(
    private val speaker: SpeakerRepository,
    private val preferences: PreferencesRepository,
) {
    suspend operator fun invoke(text: String): SaveAddressOutcome {
        val address = when (val input = R1Address.parse(text)) {
            is AddressInput.Invalid -> return SaveAddressOutcome.Invalid(input.reason)
            is AddressInput.Valid -> input.address
        }
        try {
            speaker.probe(address)
        } catch (e: CancellationException) {
            throw e
        } catch (e: SpeakerException) {
            return SaveAddressOutcome.Unreachable(address, e)
        }
        preferences.saveAddress(address)
        return SaveAddressOutcome.Saved(address)
    }
}
