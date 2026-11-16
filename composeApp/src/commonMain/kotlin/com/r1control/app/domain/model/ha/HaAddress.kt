package com.r1control.app.domain.model.ha

import com.r1control.app.domain.model.AddressInput
import com.r1control.app.domain.model.R1Address

/**
 * Where Home Assistant listens and the long-lived token that opens it.
 * [toString] leaves the token out: an address is printed in messages and
 * test failures, and a token that reaches a log is a key to the house.
 */
data class HaAddress(val host: String, val port: Int, val token: String) {
    /** The form a person types and reads back: `host:port`. */
    val hostAndPort: String get() = "$host:$port"

    override fun toString(): String = hostAndPort

    companion object {
        const val DEFAULT_PORT = 8123

        /**
         * What was typed in the address and token fields, decided once. The
         * address takes the same shapes as the R1's, with Home Assistant's port
         * when none is typed. A token pasted with a trailing newline is trimmed;
         * one with whitespace inside is not a token.
         */
        fun parse(addressText: String, tokenText: String): HaInput {
            val address = when (val parsed = R1Address.parseHostPort(addressText, DEFAULT_PORT)) {
                is AddressInput.Invalid -> return HaInput.Invalid(
                    when (parsed.reason) {
                        AddressInput.Reason.Empty -> HaInput.Reason.EmptyAddress
                        AddressInput.Reason.BadHost -> HaInput.Reason.BadHost
                        AddressInput.Reason.BadPort -> HaInput.Reason.BadPort
                    },
                )
                is AddressInput.Valid -> parsed.address
            }
            val token = tokenText.trim()
            if (token.isEmpty()) return HaInput.Invalid(HaInput.Reason.EmptyToken)
            if (token.any { it.isWhitespace() }) return HaInput.Invalid(HaInput.Reason.BadToken)
            return HaInput.Valid(HaAddress(address.host, address.port, token))
        }
    }
}

/** The answer to "is this text an address and a token", with the reason when it is not. */
sealed interface HaInput {
    data class Valid(val address: HaAddress) : HaInput
    data class Invalid(val reason: Reason) : HaInput

    enum class Reason { EmptyAddress, BadHost, BadPort, EmptyToken, BadToken }
}

/** The connection kept on this phone; `Unset` until it is first set up. */
sealed interface SavedHaAddress {
    data object Unset : SavedHaAddress
    data class Set(val address: HaAddress) : SavedHaAddress
}
