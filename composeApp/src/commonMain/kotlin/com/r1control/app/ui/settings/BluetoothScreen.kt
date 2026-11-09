package com.r1control.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import org.jetbrains.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.r1control.app.domain.model.BluetoothDevice
import com.r1control.app.domain.model.BluetoothState
import com.r1control.app.domain.repository.SpeakerException
import com.r1control.app.ui.Loadable
import com.r1control.app.ui.Problem
import com.r1control.app.ui.components.ConfirmDialog
import com.r1control.app.ui.components.LoadableContent
import com.r1control.app.ui.components.ProblemBanner
import com.r1control.app.ui.components.SettingsRow
import com.r1control.app.ui.components.SwitchRow
import com.r1control.app.ui.components.TonalButton
import com.r1control.app.ui.i18n.EnglishStrings
import com.r1control.app.ui.i18n.LocalStrings
import com.r1control.app.ui.i18n.Strings
import com.r1control.app.ui.i18n.VietnameseStrings
import com.r1control.app.ui.i18n.describe
import com.r1control.app.ui.icons.R1Icons
import com.r1control.app.ui.theme.R1Theme
import com.r1control.app.ui.theme.Radius
import com.r1control.app.ui.theme.Space
import com.r1control.app.ui.theme.Tokens

class BluetoothActions(
    val onEnabled: (Boolean) -> Unit = {},
    val onAutoReconnect: (Boolean) -> Unit = {},
    val onScan: () -> Unit = {},
    val onReceive: () -> Unit = {},
    val onPair: (BluetoothDevice) -> Unit = {},
    val onConnect: (BluetoothDevice) -> Unit = {},
    val onDisconnect: (BluetoothDevice) -> Unit = {},
    val onForget: (BluetoothDevice) -> Unit = {},
    val onForgetConfirmed: () -> Unit = {},
    val onForgetCancelled: () -> Unit = {},
    val onRetry: () -> Unit = {},
)

internal fun BluetoothViewModel.actions() = BluetoothActions(
    ::onEnabled, ::onAutoReconnect, ::scan, ::receive, ::pair, ::connect, ::disconnect, ::askForget,
    ::confirmForget, ::cancelForget, ::retry,
)

/** The Bluetooth page: the adapter's switch, and the way into what it is for. */
@Composable
fun BluetoothMenu(viewModel: BluetoothViewModel, onOpen: (SettingsPage) -> Unit) {
    val state by viewModel.state.collectAsState()
    BluetoothMenuContent(state, viewModel.actions(), onOpen)
}

@Composable
fun BluetoothMenuContent(state: BluetoothScreenState, actions: BluetoothActions, onOpen: (SettingsPage) -> Unit) {
    val strings = LocalStrings.current
    BluetoothPage(state, actions, offIsAPage = false) { bt ->
        SwitchRow(strings.bluetoothEnabled, bt.enabled, actions.onEnabled, enabled = bt.supported)
        if (!bt.supported) {
            Text(
                strings.btUnsupported,
                modifier = Modifier.padding(horizontal = Space.Gutter),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else if (bt.enabled) {
            SettingsRow(
                R1Icons.Speaker, strings.btOutputTitle, { onOpen(SettingsPage.BluetoothOutput) },
                subtitle = if (bt.hasOutput) strings.btSummaryOutput(bt.outputName) else strings.btSummaryNone,
            )
            SettingsRow(
                R1Icons.Phone, strings.btInputTitle, { onOpen(SettingsPage.BluetoothInput) },
                subtitle = when {
                    bt.hasIncoming -> strings.btIncoming(bt.incomingName)
                    bt.isOpenForPairing -> strings.btWaitingForPhone(bt.receiveSecondsLeft)
                    else -> ""
                },
            )
        }
    }
}

/**
 * What every Bluetooth page stands on: a scrolling column, the problem banner,
 * the read's spinner or failure, and the forget question. With the adapter off
 * or missing, a sub-page ([offIsAPage]) says so and offers to turn it on
 * instead of showing controls that would do nothing.
 */
@Composable
internal fun BluetoothPage(
    state: BluetoothScreenState,
    actions: BluetoothActions,
    offIsAPage: Boolean = true,
    content: @Composable (BluetoothState) -> Unit,
) {
    val strings = LocalStrings.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = Space.XLarge)) {
        val problem = state.problem
        if (problem is Problem.Failed) {
            ProblemBanner(strings.describe(problem.error), Modifier.padding(horizontal = Space.Gutter, vertical = Space.Small))
        }
        LoadableContent(state.bluetooth, actions.onRetry) { bt ->
            if (offIsAPage && (!bt.supported || !bt.enabled)) {
                Column(Modifier.padding(horizontal = Space.Gutter), verticalArrangement = Arrangement.spacedBy(Space.Small)) {
                    StatusCard(if (bt.supported) strings.btOff else strings.btUnsupported, active = false)
                    if (bt.supported) TonalButton(strings.btTurnOn, { actions.onEnabled(true) }, Modifier.fillMaxWidth())
                }
            } else {
                content(bt)
            }
        }
    }
    val ask = state.forget
    if (ask is ForgetAsk.Ask) {
        ConfirmDialog(
            text = strings.btForgetConfirm(ask.device.name),
            confirmText = strings.btForget,
            cancelText = strings.cancel,
            onConfirm = actions.onForgetConfirmed,
            onCancel = actions.onForgetCancelled,
        )
    }
}

@Composable
internal fun StatusCard(text: String, active: Boolean) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.Row)).background(Tokens.Card).padding(Space.Large),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (active) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(Tokens.Accent))
            Box(Modifier.size(Space.Medium))
        }
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

/** A device's name and, under it, what it is doing. The address is for the adapter; the name is what the household knows. */
@Composable
internal fun DeviceHeader(device: BluetoothDevice) {
    val strings = LocalStrings.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.Small)) {
        if (device.connected || device.streamingIn) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(Tokens.Accent))
        }
        Column(Modifier.weight(1f)) {
            Text(device.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val sub = when {
                device.streamingIn -> strings.btStreamingIn
                device.connected -> strings.btPlayingThrough
                device.bonded -> strings.btPaired
                else -> ""
            }
            if (sub.isNotEmpty()) {
                Text(
                    sub,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (device.connected || device.streamingIn) Tokens.Accent else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Undoing a pairing is rarer than using one, so it is a word, not a button of the same weight. */
@Composable
internal fun QuietAction(text: String, onClick: () -> Unit) {
    Box(
        Modifier.heightIn(min = 48.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onClick).padding(horizontal = Space.Large),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

// ---- previews ----

@Composable
internal fun BluetoothPreviewHost(strings: Strings, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalStrings provides strings) {
        R1Theme { Box(Modifier.background(Tokens.Ground)) { content() } }
    }
}

internal fun previewDevice(
    address: String, name: String, bonded: Boolean = true, connected: Boolean = false, streamingIn: Boolean = false, phone: Boolean = false,
) = BluetoothDevice(address, name, bonded, audio = !phone, connected = connected, streamingIn = streamingIn, phone = phone)

internal fun previewBluetooth(
    enabled: Boolean = true,
    incoming: String = "",
    connected: String = "",
    devices: List<BluetoothDevice> = emptyList(),
    receive: Int = 0,
    scanning: Boolean = false,
) = BluetoothState(
    supported = true, enabled = enabled, scanning = scanning, autoReconnect = true, adapterName = "Phicomm_R1_2E2B",
    receiveSecondsLeft = receive, incomingAddress = incoming, connectedAddress = connected, busy = "", error = "", devices = devices,
)

internal val previewDevices = listOf(
    previewDevice("14:C1:4E:75:70:64", "Kitchen speaker"),
    previewDevice("F4:4E:FD:7A:0C:92", "UGREEN-BT305"),
    previewDevice("A8:5B:B7:11:22:33", "Lan's iPhone", phone = true),
)

internal fun previewScreen(bt: BluetoothState) = BluetoothScreenState(Loadable.Loaded(bt), Problem.None, ForgetAsk.None)

@Preview
@Composable
private fun BluetoothMenuPreview() = BluetoothPreviewHost(EnglishStrings) {
    BluetoothMenuContent(previewScreen(previewBluetooth(connected = "14:C1:4E:75:70:64", devices = previewDevices)), BluetoothActions()) {}
}

@Preview
@Composable
private fun BluetoothMenuReceivingPreview() = BluetoothPreviewHost(VietnameseStrings) {
    BluetoothMenuContent(previewScreen(previewBluetooth(receive = 87, devices = previewDevices)), BluetoothActions()) {}
}

@Preview
@Composable
private fun BluetoothMenuOffPreview() = BluetoothPreviewHost(EnglishStrings) {
    BluetoothMenuContent(previewScreen(previewBluetooth(enabled = false)), BluetoothActions()) {}
}

@Preview
@Composable
private fun BluetoothMenuLoadingPreview() = BluetoothPreviewHost(EnglishStrings) {
    BluetoothMenuContent(BluetoothScreenState(Loadable.Loading, Problem.None, ForgetAsk.None), BluetoothActions()) {}
}

@Preview
@Composable
private fun BluetoothMenuFailedPreview() = BluetoothPreviewHost(EnglishStrings) {
    BluetoothMenuContent(
        BluetoothScreenState(Loadable.Failed(SpeakerException.Unreachable(RuntimeException("x"))), Problem.None, ForgetAsk.None),
        BluetoothActions(),
    ) {}
}
