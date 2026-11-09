package com.r1control.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.jetbrains.compose.ui.tooling.preview.Preview
import com.r1control.app.domain.model.BluetoothDevice
import com.r1control.app.domain.model.BluetoothState
import com.r1control.app.ui.components.SectionLabel
import com.r1control.app.ui.components.TonalButton
import com.r1control.app.ui.i18n.EnglishStrings
import com.r1control.app.ui.i18n.LocalStrings
import com.r1control.app.ui.i18n.Strings
import com.r1control.app.ui.i18n.VietnameseStrings
import com.r1control.app.ui.theme.Space

/** The R1 receiving music from a phone: open it to be found, and see who is playing in. */
@Composable
fun BluetoothInputScreen(viewModel: BluetoothViewModel) {
    val state by viewModel.state.collectAsState()
    BluetoothInputContent(state, viewModel.actions())
}

@Composable
fun BluetoothInputContent(state: BluetoothScreenState, actions: BluetoothActions) {
    val strings = LocalStrings.current
    BluetoothPage(state, actions) { bt ->
        Column(
            Modifier.fillMaxWidth().padding(horizontal = Space.Gutter),
            verticalArrangement = Arrangement.spacedBy(Space.Small),
        ) {
            StatusCard(inputStatus(bt, strings), active = bt.hasIncoming || bt.isOpenForPairing)
            TonalButton(strings.btAllowPhone, actions.onReceive, Modifier.fillMaxWidth(), enabled = !bt.isOpenForPairing)
        }
        val phones = bt.inputDevices
        if (phones.isNotEmpty()) {
            SectionLabel(strings.btPhones, Modifier.padding(horizontal = Space.Gutter))
            phones.forEach { PhoneRow(it, actions) }
        }
    }
}

private fun inputStatus(bt: BluetoothState, strings: Strings): String = when {
    bt.error.isNotEmpty() -> strings.btError(bt.error)
    bt.isOpenForPairing -> strings.btReceiving(bt.adapterName, bt.receiveSecondsLeft)
    bt.hasIncoming -> strings.btIncoming(bt.incomingName)
    else -> strings.btInputHint
}

@Composable
private fun PhoneRow(device: BluetoothDevice, actions: BluetoothActions) {
    val strings = LocalStrings.current
    Row(Modifier.fillMaxWidth().padding(start = Space.Gutter, end = Space.Small, top = Space.Small, bottom = Space.Small), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) { DeviceHeader(device) }
        if (device.bonded) QuietAction(strings.btForget) { actions.onForget(device) }
    }
}

@Preview
@Composable
private fun InputIdlePreview() = BluetoothPreviewHost(EnglishStrings) {
    BluetoothInputContent(previewScreen(previewBluetooth(devices = previewDevices)), BluetoothActions())
}

@Preview
@Composable
private fun InputOpenPreview() = BluetoothPreviewHost(VietnameseStrings) {
    BluetoothInputContent(previewScreen(previewBluetooth(receive = 87, devices = previewDevices)), BluetoothActions())
}

@Preview
@Composable
private fun InputStreamingPreview() = BluetoothPreviewHost(EnglishStrings) {
    BluetoothInputContent(
        previewScreen(previewBluetooth(incoming = "F4:4E:FD:7A:0C:92", devices = previewDevices.map { if (it.name == "UGREEN-BT305") it.copy(streamingIn = true) else it })),
        BluetoothActions(),
    )
}
