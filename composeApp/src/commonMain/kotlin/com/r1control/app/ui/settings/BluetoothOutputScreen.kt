package com.r1control.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Row
import org.jetbrains.compose.ui.tooling.preview.Preview
import com.r1control.app.domain.model.BluetoothDevice
import com.r1control.app.domain.model.BluetoothState
import com.r1control.app.ui.Loadable
import com.r1control.app.ui.Problem
import com.r1control.app.ui.components.SwitchRow
import com.r1control.app.ui.components.TonalButton
import com.r1control.app.ui.i18n.EnglishStrings
import com.r1control.app.ui.i18n.LocalStrings
import com.r1control.app.ui.i18n.Strings
import com.r1control.app.ui.i18n.VietnameseStrings
import com.r1control.app.ui.theme.Space

/** The R1 playing out to a Bluetooth speaker: find one, pair it, connect it. */
@Composable
fun BluetoothOutputScreen(viewModel: BluetoothViewModel) {
    val state by viewModel.state.collectAsState()
    BluetoothOutputContent(state, viewModel.actions())
}

@Composable
fun BluetoothOutputContent(state: BluetoothScreenState, actions: BluetoothActions) {
    val strings = LocalStrings.current
    BluetoothPage(state, actions) { bt ->
        Column(Modifier.padding(horizontal = Space.Gutter)) {
            StatusCard(outputStatus(bt, strings), active = bt.hasOutput)
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = Space.Gutter, vertical = Space.Small)) {
            TonalButton(
                if (bt.scanning) strings.btScanning else strings.bluetoothScan,
                actions.onScan, Modifier.fillMaxWidth(), enabled = !bt.scanning,
            )
        }
        val speakers = bt.outputDevices
        if (speakers.isEmpty()) {
            Text(
                strings.btNoDevices,
                modifier = Modifier.padding(horizontal = Space.Gutter, vertical = Space.Small),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        speakers.forEach { SpeakerRow(it, actions) }
        SwitchRow(strings.bluetoothAutoReconnect, bt.autoReconnect, actions.onAutoReconnect, modifier = Modifier.padding(top = Space.Large))
    }
}

private fun outputStatus(bt: BluetoothState, strings: Strings): String = when {
    bt.error.isNotEmpty() -> strings.btError(bt.error)
    bt.busy == "pairing" -> strings.btPairing
    bt.busy == "connecting" -> strings.btConnecting
    bt.hasOutput -> strings.btOutput(bt.outputName)
    else -> strings.btNone
}

@Composable
private fun SpeakerRow(device: BluetoothDevice, actions: BluetoothActions) {
    val strings = LocalStrings.current
    Column(
        Modifier.fillMaxWidth().padding(horizontal = Space.Gutter, vertical = Space.Small),
        verticalArrangement = Arrangement.spacedBy(Space.Small),
    ) {
        DeviceHeader(device)
        Row(horizontalArrangement = Arrangement.spacedBy(Space.Small), verticalAlignment = Alignment.CenterVertically) {
            when {
                device.connected -> TonalButton(strings.btDisconnect, { actions.onDisconnect(device) })
                device.bonded -> TonalButton(strings.btConnect, { actions.onConnect(device) })
                else -> TonalButton(strings.btPair, { actions.onPair(device) })
            }
            if (device.bonded) QuietAction(strings.btForget) { actions.onForget(device) }
        }
    }
}

@Preview
@Composable
private fun OutputIdlePreview() = BluetoothPreviewHost(EnglishStrings) {
    BluetoothOutputContent(previewScreen(previewBluetooth(devices = previewDevices)), BluetoothActions())
}

@Preview
@Composable
private fun OutputConnectedPreview() = BluetoothPreviewHost(VietnameseStrings) {
    BluetoothOutputContent(
        previewScreen(previewBluetooth(connected = "14:C1:4E:75:70:64", devices = previewDevices.map { if (it.name == "Kitchen speaker") it.copy(connected = true) else it })),
        BluetoothActions(),
    )
}

@Preview
@Composable
private fun OutputNoSpeakersPreview() = BluetoothPreviewHost(EnglishStrings) {
    BluetoothOutputContent(previewScreen(previewBluetooth(scanning = true)), BluetoothActions())
}

@Preview
@Composable
private fun OutputOffPreview() = BluetoothPreviewHost(EnglishStrings) {
    BluetoothOutputContent(previewScreen(previewBluetooth(enabled = false)), BluetoothActions())
}
