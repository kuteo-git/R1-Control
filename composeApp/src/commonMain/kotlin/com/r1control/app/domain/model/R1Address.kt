package com.r1control.app.domain.model

/** Where the speaker's control server listens: plain HTTP on the house wifi. */
data class R1Address(val host: String, val port: Int) {
    val baseUrl: String get() = "http://$host:$port"

    /** The form a person types and reads back: `host:port`. */
    override fun toString(): String = "$host:$port"

    companion object {
        const val DEFAULT_PORT = 8088

        /**
         * What a person typed in the address field, decided once.
         *
         * Accepts `192.168.1.20`, `192.168.1.20:8088`, `r1.local:8088`, with an
         * optional `http://` (any case) in front and a trailing `/`. Rejects everything
         * else (paths, spaces, IPv6, ports outside 1..65535) rather than
         * guessing: a guessed address fails later as "could not reach", which
         * names the wrong problem.
         */
        fun parse(text: String): AddressInput {
            var rest = text.trim()
            if (rest.isEmpty()) return AddressInput.Invalid(AddressInput.Reason.Empty)
            if (rest.startsWith("http://", ignoreCase = true)) rest = rest.substring("http://".length)
            rest = rest.removeSuffix("/")
            // Another scheme is not an address for a speaker that only speaks plain HTTP.
            if ("://" in rest) return AddressInput.Invalid(AddressInput.Reason.BadHost)

            val colon = rest.lastIndexOf(':')
            val host = if (colon < 0) rest else rest.substring(0, colon)
            val portText = if (colon < 0) "" else rest.substring(colon + 1)

            if (!isHost(host)) return AddressInput.Invalid(AddressInput.Reason.BadHost)
            val port = when {
                colon < 0 -> DEFAULT_PORT
                portText.isEmpty() || !portText.all { it in '0'..'9' } || portText.length > 5 ->
                    return AddressInput.Invalid(AddressInput.Reason.BadPort)
                else -> portText.toInt()
            }
            if (port !in 1..65535) return AddressInput.Invalid(AddressInput.Reason.BadPort)
            return AddressInput.Valid(R1Address(canonicalHost(host), port))
        }

        private fun isIpv4(host: String): Boolean = host.split('.').all { it.isNotEmpty() && it.all { c -> c in '0'..'9' } }

        /**
         * `192.168.001.020` is `192.168.1.20`: nobody types octal, but iOS resolves a
         * numeric host with `inet_aton`, which reads `020` as 16.
         */
        private fun canonicalHost(host: String): String =
            if (isIpv4(host)) host.split('.').joinToString(".") { it.toInt().toString() } else host.lowercase()

        private fun isHost(host: String): Boolean {
            if (host.isEmpty() || host.length > 253) return false
            val labels = host.split('.')
            if (isIpv4(host)) {
                return labels.size == 4 && labels.all { it.length <= 3 && it.toInt() <= 255 }
            }
            return labels.all { label ->
                label.isNotEmpty() && label.length <= 63 &&
                    label.first() != '-' && label.last() != '-' &&
                    label.all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it == '-' }
            }
        }
    }
}

/** The answer to "is this text an address", with the reason when it is not. */
sealed interface AddressInput {
    data class Valid(val address: R1Address) : AddressInput
    data class Invalid(val reason: Reason) : AddressInput

    enum class Reason { Empty, BadHost, BadPort }
}

/** The address kept on this phone; `Unset` until the first setup. */
sealed interface SavedAddress {
    data object Unset : SavedAddress
    data class Set(val address: R1Address) : SavedAddress
}
