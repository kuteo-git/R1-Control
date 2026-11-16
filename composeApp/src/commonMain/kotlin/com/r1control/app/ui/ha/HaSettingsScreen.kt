package com.r1control.app.ui.ha

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.r1control.app.domain.model.ha.HaInput
import com.r1control.app.ui.components.ConfirmDialog
import com.r1control.app.ui.components.PrimaryButton
import com.r1control.app.ui.components.R1Icon
import com.r1control.app.ui.components.R1TextField
import com.r1control.app.ui.components.TonalButton
import kotlinx.coroutines.flow.StateFlow
import com.r1control.app.ui.i18n.EnglishStrings
import com.r1control.app.ui.i18n.LocalStrings
import com.r1control.app.ui.i18n.Strings
import com.r1control.app.ui.i18n.VietnameseStrings
import com.r1control.app.ui.i18n.phoneAsksForLocalNetwork
import com.r1control.app.ui.icons.R1Icons
import com.r1control.app.ui.theme.R1Theme
import com.r1control.app.ui.theme.Space
import com.r1control.app.ui.theme.Tokens
import org.jetbrains.compose.ui.tooling.preview.Preview

/** The address field, the masked token field, the words under them and the Connect button. */
@Composable
fun HaConnectForm(
    address: String,
    token: String,
    status: HaFormStatus,
    onAddress: (String) -> Unit,
    onToken: (String) -> Unit,
    onConnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalStrings.current
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.Medium)) {
        R1TextField(
            value = address,
            onValueChange = onAddress,
            hint = strings.haAddressHint,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false, keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
        )
        R1TextField(
            value = token,
            onValueChange = onToken,
            hint = strings.haTokenHint,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false, keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            onDone = onConnect,
            visualTransformation = PasswordVisualTransformation(),
        )
        val message = haFormMessage(status, strings)
        if (message.isNotEmpty()) {
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = when (status) {
                    HaFormStatus.Connected -> Tokens.Accent
                    HaFormStatus.Checking -> Tokens.OnGroundMuted
                    else -> Tokens.Error
                },
            )
        }
        PrimaryButton(text = strings.connect, onClick = onConnect, modifier = Modifier.fillMaxWidth(), busy = status is HaFormStatus.Checking)
    }
}

/** The words under the fields. A refused token and an unanswering server have different fixes. */
internal fun haFormMessage(status: HaFormStatus, strings: Strings): String = when (status) {
    HaFormStatus.Idle -> ""
    HaFormStatus.Checking -> strings.haChecking
    is HaFormStatus.Invalid -> strings.haInvalid(status.reason)
    HaFormStatus.TokenRefused -> strings.haTokenRefused
    is HaFormStatus.Unreachable -> strings.haUnreachable(status.address, phoneAsksForLocalNetwork)
    HaFormStatus.Connected -> strings.haConnected
}

/** The page behind the gear on the Home Assistant tab. Its own settings, apart from the R1's. */
@Composable
fun HaSettingsScreen(viewModel: HaSettingsViewModel, resyncState: StateFlow<HaResync>, onResync: () -> Unit) {
    val address by viewModel.address.collectAsState()
    val token by viewModel.token.collectAsState()
    val status by viewModel.status.collectAsState()
    val resync by resyncState.collectAsState()
    HaSettingsContent(address, token, status, resync, viewModel::onAddress, viewModel::onToken, viewModel::connect, onResync)
}

@Composable
fun HaSettingsContent(
    address: String,
    token: String,
    status: HaFormStatus,
    resync: HaResync,
    onAddress: (String) -> Unit,
    onToken: (String) -> Unit,
    onConnect: () -> Unit,
    onResync: () -> Unit,
) {
    val strings = LocalStrings.current
    var confirming by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Space.Gutter + Space.Small, vertical = Space.Large),
        verticalArrangement = Arrangement.spacedBy(Space.Large),
    ) {
        Text(strings.haWelcomeBody, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        HaConnectForm(address, token, status, onAddress, onToken, onConnect)
        Column(verticalArrangement = Arrangement.spacedBy(Space.Small)) {
            Text(strings.haSyncTitle, style = MaterialTheme.typography.titleMedium)
            Text(strings.haSyncBody, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TonalButton(strings.haSyncButton, onClick = { confirming = true }, modifier = Modifier.fillMaxWidth())
            val message = haSyncMessage(resync, strings)
            if (message.isNotEmpty()) {
                Text(
                    message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (resync == HaResync.Failed) Tokens.Error else Tokens.OnGroundMuted,
                )
            }
        }
    }
    if (confirming) {
        ConfirmDialog(
            text = strings.haSyncWarning,
            confirmText = strings.haSyncConfirm,
            cancelText = strings.cancel,
            onConfirm = {
                onResync()
                confirming = false
            },
            onCancel = { confirming = false },
        )
    }
}

/** The line under the sync button: nothing while idle, then its progress or its result. */
internal fun haSyncMessage(resync: HaResync, strings: Strings): String = when (resync) {
    HaResync.Idle -> ""
    HaResync.Running -> strings.haSyncing
    HaResync.Done -> strings.haSynced
    HaResync.Failed -> strings.haSyncFailed
}

/** The tab's own form, before anything is saved or after the token was refused. */
@Composable
fun HaSetupContent(
    address: String,
    token: String,
    status: HaFormStatus,
    refused: Boolean,
    onAddress: (String) -> Unit,
    onToken: (String) -> Unit,
    onConnect: () -> Unit,
) {
    val strings = LocalStrings.current
    Box(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState())) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 420.dp)
                .padding(horizontal = Space.Gutter + Space.Small, vertical = Space.XLarge)
                .align(Alignment.Center),
            verticalArrangement = Arrangement.spacedBy(Space.Large),
        ) {
            R1Icon(R1Icons.Home, null, tint = Tokens.Accent, size = 56.dp)
            Text(strings.haWelcomeTitle, style = MaterialTheme.typography.displaySmall)
            Text(strings.haWelcomeBody, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (refused && status == HaFormStatus.Idle) {
                Text(strings.haTokenRefused, style = MaterialTheme.typography.bodyMedium, color = Tokens.Error)
            }
            HaConnectForm(address, token, status, onAddress, onToken, onConnect, Modifier.padding(top = Space.Medium))
        }
    }
}

// ---- previews ----

@Composable
private fun PreviewHost(strings: Strings, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalStrings provides strings) {
        R1Theme { Box(Modifier.background(Tokens.Ground).statusBarsPadding()) { content() } }
    }
}

@Preview
@Composable
private fun SetupEmptyPreview() = PreviewHost(EnglishStrings) { HaSetupContent("", "", HaFormStatus.Idle, false, {}, {}, {}) }

@Preview
@Composable
private fun SetupCheckingPreview() = PreviewHost(EnglishStrings) { HaSetupContent("192.168.1.10:8123", "abcdef", HaFormStatus.Checking, false, {}, {}, {}) }

@Preview
@Composable
private fun SetupInvalidPreview() =
    PreviewHost(EnglishStrings) { HaSetupContent("ha box", "", HaFormStatus.Invalid(HaInput.Reason.BadHost), false, {}, {}, {}) }

@Preview
@Composable
private fun SetupRefusedPreview() = PreviewHost(EnglishStrings) { HaSetupContent("192.168.1.10:8123", "old", HaFormStatus.Idle, true, {}, {}, {}) }

@Preview
@Composable
private fun SetupUnreachablePreview() =
    PreviewHost(EnglishStrings) { HaSetupContent("10.0.0.9:8123", "abc", HaFormStatus.Unreachable("10.0.0.9:8123"), false, {}, {}, {}) }

@Preview
@Composable
private fun SettingsPagePreview() =
    PreviewHost(EnglishStrings) { HaSettingsContent("192.168.1.10:8123", "token", HaFormStatus.Idle, HaResync.Idle, {}, {}, {}, {}) }

@Preview
@Composable
private fun SettingsSyncedPreview() =
    PreviewHost(VietnameseStrings) { HaSettingsContent("192.168.1.10:8123", "token", HaFormStatus.Connected, HaResync.Done, {}, {}, {}, {}) }

@Preview
@Composable
private fun SetupVietnamesePreview() = PreviewHost(VietnameseStrings) { HaSetupContent("", "", HaFormStatus.Idle, false, {}, {}, {}) }
