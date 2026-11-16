package com.r1control.app.ui.ha

import com.r1control.app.domain.model.ha.HaAddress
import com.r1control.app.domain.model.ha.HaInput
import com.r1control.app.domain.model.ha.SavedHaAddress
import com.r1control.app.domain.repository.HaSettingsRepository
import com.r1control.app.domain.usecase.ConnectHa
import com.r1control.app.domain.usecase.ConnectHaOutcome
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface HaFormStatus {
    data object Idle : HaFormStatus
    data object Checking : HaFormStatus
    data class Invalid(val reason: HaInput.Reason) : HaFormStatus
    data object TokenRefused : HaFormStatus

    /** Nothing answered at [address]. */
    data class Unreachable(val address: String) : HaFormStatus
    data object Connected : HaFormStatus
}

/**
 * The address and token fields. Each is its own flow, never routed through a combined state, so a
 * keystroke does not wait on anything. [onConnected] runs once Home Assistant has accepted the
 * pair and it is saved.
 */
class HaSettingsViewModel(
    settings: HaSettingsRepository,
    private val connectHa: ConnectHa,
    private val scope: CoroutineScope,
    private val onConnected: (HaAddress) -> Unit,
) {
    private val addressFlow = MutableStateFlow("")
    private val tokenFlow = MutableStateFlow("")
    private val statusFlow = MutableStateFlow<HaFormStatus>(HaFormStatus.Idle)
    private var checking: Job? = null

    val address: StateFlow<String> = addressFlow.asStateFlow()
    val token: StateFlow<String> = tokenFlow.asStateFlow()
    val status: StateFlow<HaFormStatus> = statusFlow.asStateFlow()

    init {
        // What is saved shows in the fields, unless a finger got there first.
        scope.launch {
            val saved = try {
                settings.saved()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                SavedHaAddress.Unset
            }
            if (saved is SavedHaAddress.Set && addressFlow.value.isEmpty() && tokenFlow.value.isEmpty()) {
                addressFlow.value = saved.address.hostAndPort
                tokenFlow.value = saved.address.token
            }
        }
    }

    fun onAddress(value: String) {
        addressFlow.value = value
        clearMessage()
    }

    fun onToken(value: String) {
        tokenFlow.value = value
        clearMessage()
    }

    private fun clearMessage() {
        if (statusFlow.value !is HaFormStatus.Checking) statusFlow.value = HaFormStatus.Idle
    }

    fun connect() {
        if (statusFlow.value is HaFormStatus.Checking) return
        val address = when (val input = HaAddress.parse(addressFlow.value, tokenFlow.value)) {
            is HaInput.Invalid -> {
                statusFlow.value = HaFormStatus.Invalid(input.reason)
                return
            }
            is HaInput.Valid -> input.address
        }
        statusFlow.value = HaFormStatus.Checking
        checking?.cancel()
        checking = scope.launch {
            when (connectHa(address)) {
                ConnectHaOutcome.Connected -> {
                    addressFlow.value = address.hostAndPort
                    statusFlow.value = HaFormStatus.Connected
                    onConnected(address)
                }
                ConnectHaOutcome.TokenRefused -> statusFlow.value = HaFormStatus.TokenRefused
                ConnectHaOutcome.Unreachable -> statusFlow.value = HaFormStatus.Unreachable(address.hostAndPort)
            }
        }
    }
}
