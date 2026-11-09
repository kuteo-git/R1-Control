package com.r1control.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import org.jetbrains.compose.ui.tooling.preview.Preview
import com.r1control.app.domain.model.APP_VERSION
import com.r1control.app.domain.repository.SpeakerException
import com.r1control.app.ui.Problem
import com.r1control.app.ui.address.AddressForm
import com.r1control.app.ui.address.AddressStatus
import com.r1control.app.ui.address.AddressViewModel
import com.r1control.app.ui.components.ConfirmDialog
import com.r1control.app.ui.components.ProblemBanner
import com.r1control.app.ui.components.SectionLabel
import com.r1control.app.ui.components.TonalButton
import com.r1control.app.ui.i18n.EnglishStrings
import com.r1control.app.ui.i18n.LocalStrings
import com.r1control.app.ui.i18n.Strings
import com.r1control.app.ui.i18n.VietnameseStrings
import com.r1control.app.ui.i18n.describe
import com.r1control.app.ui.theme.R1Theme
import com.r1control.app.ui.theme.Space
import com.r1control.app.ui.theme.Tokens

@Composable
fun AdvancedScreen(address: AddressViewModel, viewModel: AdvancedViewModel) {
    val text by address.text.collectAsState()
    val status by address.status.collectAsState()
    val state by viewModel.state.collectAsState()
    AdvancedContent(
        text, status, state, address::onText, address::save,
        viewModel::askRestart, viewModel::confirmRestart, viewModel::cancelRestart,
    )
}

@Composable
fun AdvancedContent(
    addressText: String,
    addressStatus: AddressStatus,
    state: AdvancedState,
    onAddressText: (String) -> Unit,
    onSaveAddress: () -> Unit,
    onAskRestart: () -> Unit,
    onConfirmRestart: () -> Unit,
    onCancelRestart: () -> Unit,
) {
    val strings = LocalStrings.current
    Column(
        Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = Space.Gutter).padding(bottom = Space.XLarge),
    ) {
        SectionLabel(strings.addressLabel)
        AddressForm(addressText, addressStatus, onAddressText, onSaveAddress, strings.save)

        SectionLabel(strings.restartSpeaker, Modifier.padding(top = Space.Large))
        val problem = state.problem
        if (problem is Problem.Failed) {
            ProblemBanner(strings.describe(problem.error), Modifier.padding(bottom = Space.Medium))
        }
        TonalButton(
            if (state.restart == RestartStep.Restarting) strings.restarting else strings.restartButton,
            onAskRestart,
            enabled = state.restart != RestartStep.Restarting,
        )
        if (state.restart == RestartStep.Restarted) {
            Text(
                strings.restartDone,
                modifier = Modifier.padding(top = Space.Medium),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Text(
            strings.appVersion(APP_VERSION),
            modifier = Modifier.padding(top = Space.XLarge),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (state.restart == RestartStep.Asking) {
        ConfirmDialog(strings.restartConfirm, strings.restartButton, strings.cancel, onConfirmRestart, onCancelRestart)
    }
}

@Composable
private fun PreviewHost(strings: Strings, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalStrings provides strings) {
        R1Theme { Box(Modifier.background(Tokens.Ground)) { content() } }
    }
}

private val idle = AdvancedState(RestartStep.Idle, Problem.None)

@Preview
@Composable
private fun AdvancedPreview() =
    PreviewHost(EnglishStrings) { AdvancedContent("10.25.113.209:8088", AddressStatus.Idle, idle, {}, {}, {}, {}, {}) }

@Preview
@Composable
private fun AdvancedAskingPreview() = PreviewHost(EnglishStrings) {
    AdvancedContent("10.25.113.209:8088", AddressStatus.Idle, AdvancedState(RestartStep.Asking, Problem.None), {}, {}, {}, {}, {})
}

@Preview
@Composable
private fun AdvancedFailedPreview() = PreviewHost(EnglishStrings) {
    AdvancedContent(
        "10.25.113.209:8088", AddressStatus.Idle,
        AdvancedState(RestartStep.Idle, Problem.Failed(SpeakerException.Unreachable(RuntimeException("x")))), {}, {}, {}, {}, {},
    )
}

@Preview
@Composable
private fun AdvancedVietnamesePreview() =
    PreviewHost(VietnameseStrings) { AdvancedContent("10.25.113.209:8088", AddressStatus.Idle, AdvancedState(RestartStep.Restarted, Problem.None), {}, {}, {}, {}, {}) }
