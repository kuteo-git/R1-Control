package com.r1control.app.domain.usecase

import com.r1control.app.domain.model.ha.HaAddress
import com.r1control.app.domain.model.ha.HaConnectionState
import com.r1control.app.domain.model.ha.SavedHaAddress
import com.r1control.app.domain.repository.HaRepository
import com.r1control.app.domain.repository.HaSettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

sealed interface ConnectHaOutcome {
    data object Connected : ConnectHaOutcome
    data object TokenRefused : ConnectHaOutcome
    data object Unreachable : ConnectHaOutcome
}

/**
 * Tries a typed address and token, and keeps them only once Home Assistant has accepted them, as
 * `SaveAddress` does for the speaker. A failed try puts the link back as it was before, so a typo
 * does not leave the saved connection replaced by one that never worked.
 */
class ConnectHa(
    private val ha: HaRepository,
    private val settings: HaSettingsRepository,
    private val verdictTimeoutMillis: Long = 15_000,
) {
    suspend operator fun invoke(address: HaAddress): ConnectHaOutcome {
        ha.connect(address)
        val verdict = withTimeoutOrNull(verdictTimeoutMillis) {
            ha.connectionState.first { it != HaConnectionState.Connecting && it != HaConnectionState.Disconnected }
        }
        return when (verdict) {
            HaConnectionState.Connected -> {
                settings.save(address)
                ConnectHaOutcome.Connected
            }
            HaConnectionState.AuthInvalid -> restoreSaved().let { ConnectHaOutcome.TokenRefused }
            else -> restoreSaved().let { ConnectHaOutcome.Unreachable }
        }
    }

    private suspend fun restoreSaved() {
        ha.disconnect()
        (settings.saved() as? SavedHaAddress.Set)?.let { ha.connect(it.address) }
    }
}
