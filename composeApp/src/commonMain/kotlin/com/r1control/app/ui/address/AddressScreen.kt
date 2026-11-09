package com.r1control.app.ui.address

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import org.jetbrains.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.r1control.app.domain.model.AddressInput
import com.r1control.app.domain.model.R1Address
import com.r1control.app.domain.repository.SpeakerException
import com.r1control.app.ui.components.PrimaryButton
import com.r1control.app.ui.components.R1Icon
import com.r1control.app.ui.components.R1TextField
import com.r1control.app.ui.i18n.EnglishStrings
import com.r1control.app.ui.i18n.LocalStrings
import com.r1control.app.ui.i18n.Strings
import com.r1control.app.ui.i18n.VietnameseStrings
import com.r1control.app.ui.i18n.phoneAsksForLocalNetwork
import com.r1control.app.ui.icons.R1Icons
import com.r1control.app.ui.theme.R1Theme
import com.r1control.app.ui.theme.Space
import com.r1control.app.ui.theme.Tokens

/**
 * The address field with its Save button and the words under it. One
 * component for the first-run page and for Settings > Advanced.
 */
@Composable
fun AddressForm(
    text: String,
    status: AddressStatus,
    onText: (String) -> Unit,
    onSave: () -> Unit,
    buttonText: String,
    modifier: Modifier = Modifier,
) {
    val strings = LocalStrings.current
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.Medium)) {
        R1TextField(
            value = text,
            onValueChange = onText,
            hint = strings.addressHint,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false, keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
            onDone = onSave,
        )
        val message = addressMessage(status, strings)
        if (message.isNotEmpty()) {
            val good = status is AddressStatus.Saved
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = if (good) Tokens.Accent else if (status is AddressStatus.Checking) Tokens.OnGroundMuted else Tokens.Error,
            )
        }
        PrimaryButton(
            text = buttonText,
            onClick = onSave,
            modifier = Modifier.fillMaxWidth(),
            busy = status is AddressStatus.Checking,
        )
    }
}

/** The words under the field. Nothing answering and the wrong thing answering are different problems with different fixes. */
internal fun addressMessage(status: AddressStatus, strings: Strings): String = when (status) {
    AddressStatus.Idle -> ""
    AddressStatus.Checking -> strings.addressChecking
    is AddressStatus.Invalid -> strings.addressInvalid(status.reason)
    is AddressStatus.Unreachable -> when (status.error) {
        is SpeakerException.Unreachable, is SpeakerException.NotConfigured -> strings.addressUnreachable(status.address.toString(), phoneAsksForLocalNetwork)
        is SpeakerException.Refused, is SpeakerException.BadReply -> strings.addressNotR1(status.address.toString())
    }
    is AddressStatus.Saved -> strings.addressSaved
}

/** Shown before the tabs until an address is saved. */
@Composable
fun FirstRunScreen(viewModel: AddressViewModel) {
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onReturned() }
    val text by viewModel.text.collectAsState()
    val status by viewModel.status.collectAsState()
    FirstRunContent(text, status, viewModel::onText, viewModel::save)
}

@Composable
fun FirstRunContent(text: String, status: AddressStatus, onText: (String) -> Unit, onSave: () -> Unit) {
    val strings = LocalStrings.current
    Box(Modifier.fillMaxSize().statusBarsPadding().imePadding().verticalScroll(rememberScrollState())) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 480.dp)
                .padding(horizontal = Space.Gutter + Space.Small, vertical = Space.XLarge)
                .align(Alignment.Center),
            verticalArrangement = Arrangement.spacedBy(Space.Large),
        ) {
            R1Icon(R1Icons.Speaker, null, tint = Tokens.Accent, size = 56.dp)
            Text(strings.welcomeTitle, style = MaterialTheme.typography.displaySmall)
            Text(strings.welcomeBody, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            AddressForm(text, status, onText, onSave, strings.connect, Modifier.padding(top = Space.Medium))
        }
    }
}

// ---- previews ----

@Composable
private fun PreviewHost(strings: Strings, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalStrings provides strings) {
        R1Theme { Box(Modifier.background(Tokens.Ground)) { content() } }
    }
}

@Preview
@Composable
private fun FirstRunEmptyPreview() = PreviewHost(EnglishStrings) { FirstRunContent("", AddressStatus.Idle, {}, {}) }

@Preview
@Composable
private fun FirstRunCheckingPreview() = PreviewHost(EnglishStrings) { FirstRunContent("10.25.113.209", AddressStatus.Checking, {}, {}) }

@Preview
@Composable
private fun FirstRunInvalidPreview() =
    PreviewHost(EnglishStrings) { FirstRunContent("r1 speaker", AddressStatus.Invalid(AddressInput.Reason.BadHost), {}, {}) }

@Preview
@Composable
private fun FirstRunUnreachablePreview() = PreviewHost(EnglishStrings) {
    FirstRunContent(
        "10.0.0.9:8088",
        AddressStatus.Unreachable(R1Address("10.0.0.9", 8088), SpeakerException.Unreachable(RuntimeException("x"))),
        {}, {},
    )
}

@Preview
@Composable
private fun FirstRunNotR1Preview() = PreviewHost(EnglishStrings) {
    FirstRunContent("10.0.0.1", AddressStatus.Unreachable(R1Address("10.0.0.1", 8088), SpeakerException.Refused("HTTP 404")), {}, {})
}

@Preview
@Composable
private fun FirstRunVietnamesePreview() = PreviewHost(VietnameseStrings) { FirstRunContent("", AddressStatus.Idle, {}, {}) }
