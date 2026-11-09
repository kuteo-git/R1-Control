package com.r1control.app.domain.model

data class BluetoothDevice(
    val address: String,
    /** Falls back to the address when the device has no name. */
    val name: String,
    val bonded: Boolean,
    val audio: Boolean,
    val connected: Boolean,
    val streamingIn: Boolean,
    val phone: Boolean,
)

/**
 * The R1's Bluetooth card. Empty strings mean "no one": [incomingAddress] is
 * the device streaming music in, [connectedAddress] the speaker the R1 plays
 * out to.
 */
data class BluetoothState(
    val supported: Boolean,
    val enabled: Boolean,
    val scanning: Boolean,
    val autoReconnect: Boolean,
    val adapterName: String,
    val receiveSecondsLeft: Int,
    val incomingAddress: String,
    val connectedAddress: String,
    /** The operation the adapter is busy with, empty when none. */
    val busy: String,
    /** The adapter's last error, empty when none. Shown, never parsed. */
    val error: String,
    val devices: List<BluetoothDevice>,
) {
    val hasIncoming: Boolean get() = incomingAddress.isNotEmpty()
    val hasOutput: Boolean get() = connectedAddress.isNotEmpty()
    val isOpenForPairing: Boolean get() = receiveSecondsLeft > 0

    val incomingName: String get() = nameOf(incomingAddress)
    val outputName: String get() = nameOf(connectedAddress)

    /**
     * What the R1 can play out to: not a phone and not what is streaming in. An
     * audio-class device that never paired is a speaker found by a scan; a
     * bonded one always shows, so a speaker that misdeclares its class does not
     * vanish once paired.
     */
    val outputDevices: List<BluetoothDevice>
        get() = devices.filter { (it.audio || it.bonded) && !it.phone && !it.streamingIn }

    /** Who plays into the R1: what streams in now, and the phones it has paired with. */
    val inputDevices: List<BluetoothDevice>
        get() = devices.filter { it.streamingIn || (it.phone && it.bonded) }

    private fun nameOf(address: String): String =
        devices.firstOrNull { it.address == address }?.name ?: address
}
