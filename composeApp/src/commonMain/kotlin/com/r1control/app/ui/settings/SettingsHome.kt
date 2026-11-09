package com.r1control.app.ui.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import org.jetbrains.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.r1control.app.domain.model.AudioSettings
import com.r1control.app.domain.model.BluetoothState
import com.r1control.app.domain.model.NewsSettings
import com.r1control.app.domain.model.formatDb
import com.r1control.app.ui.Loadable
import com.r1control.app.ui.components.SettingsRow
import com.r1control.app.ui.i18n.EnglishStrings
import com.r1control.app.ui.i18n.LocalStrings
import com.r1control.app.ui.i18n.Strings
import com.r1control.app.ui.i18n.VietnameseStrings
import com.r1control.app.ui.i18n.languageChoiceName
import kotlinx.coroutines.flow.StateFlow
import com.r1control.app.domain.model.LanguageChoice
import com.r1control.app.ui.icons.R1Icons
import com.r1control.app.ui.theme.R1Theme
import com.r1control.app.ui.theme.Tokens

/** [BluetoothOutput] and [BluetoothInput] are the two pages of the Bluetooth menu: the R1 playing out, and the R1 receiving. */
enum class SettingsPage { Home, Audio, Voice, News, Bluetooth, BluetoothOutput, BluetoothInput, Language, Advanced }

fun SettingsPage.title(strings: Strings): String = when (this) {
    SettingsPage.Home -> strings.settings
    SettingsPage.Audio -> strings.settingsAudio
    SettingsPage.Voice -> strings.settingsVoice
    SettingsPage.News -> strings.settingsNews
    SettingsPage.Bluetooth -> strings.settingsBluetooth
    SettingsPage.BluetoothOutput -> strings.btOutputPage
    SettingsPage.BluetoothInput -> strings.btInputPage
    SettingsPage.Language -> strings.settingsLanguage
    SettingsPage.Advanced -> strings.settingsAdvanced
}

@Composable
fun SettingsHome(viewModel: SettingsHomeViewModel, address: String, language: StateFlow<LanguageChoice>, onOpen: (SettingsPage) -> Unit) {
    val summary by viewModel.state.collectAsState()
    val choice by language.collectAsState()
    SettingsHomeContent(summary, address, choice, onOpen)
}

@Composable
fun SettingsHomeContent(summary: Loadable<SettingsSummary>, address: String, language: LanguageChoice, onOpen: (SettingsPage) -> Unit) {
    val strings = LocalStrings.current
    // Until the values arrive, or if they cannot, the rows are still the way in: they just say less.
    val known = (summary as? Loadable.Loaded)?.value
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        SettingsRow(
            R1Icons.Volume, strings.settingsAudio, { onOpen(SettingsPage.Audio) },
            subtitle = known?.let { strings.audioSummary(formatDb(it.audio.loudnessMb, signed = true), it.audio.eqEnabled) }.orEmpty(),
        )
        SettingsRow(R1Icons.Mic, strings.settingsVoice, { onOpen(SettingsPage.Voice) }, subtitle = known?.voice.orEmpty())
        SettingsRow(
            R1Icons.News, strings.settingsNews, { onOpen(SettingsPage.News) },
            subtitle = known?.let { strings.newsSummary(it.news.enabled, it.news.time) }.orEmpty(),
        )
        SettingsRow(
            R1Icons.Bluetooth, strings.settingsBluetooth, { onOpen(SettingsPage.Bluetooth) },
            subtitle = known?.let { bluetoothLine(it.bluetooth, strings) }.orEmpty(),
        )
        SettingsRow(R1Icons.Language, strings.settingsLanguage, { onOpen(SettingsPage.Language) }, subtitle = strings.languageChoiceName(language))
        SettingsRow(R1Icons.Tune, strings.settingsAdvanced, { onOpen(SettingsPage.Advanced) }, subtitle = address)
    }
}

/**
 * Pages slide as on iOS: forward, the new page comes in over the old one from
 * the right; back, the old one leaves to the right and uncovers the page under it.
 * Each page is drawn on the ground colour, or the two would show through each other.
 */
@Composable
fun <T> PageSlide(target: T, depth: (T) -> Int, content: @Composable (T) -> Unit) {
    AnimatedContent(
        targetState = target,
        transitionSpec = {
            val forward = depth(targetState) > depth(initialState)
            val enter = slideInHorizontally { width -> if (forward) width else -width / 4 }
            val exit = slideOutHorizontally { width -> if (forward) -width / 4 else width }
            (enter togetherWith exit).apply { targetContentZIndex = if (forward) 1f else -1f }
        },
    ) { shown ->
        Box(Modifier.fillMaxSize().background(Tokens.Ground)) { content(shown) }
    }
}

/** The Bluetooth row's second line: what the speaker's sound is doing, in a few words. */
internal fun bluetoothLine(bt: BluetoothState, strings: Strings): String = when {
    !bt.supported || !bt.enabled -> strings.btSummaryOff
    bt.hasIncoming -> strings.btIncoming(bt.incomingName)
    bt.hasOutput -> strings.btSummaryOutput(bt.outputName)
    else -> strings.btSummaryNone
}

@Composable
private fun PreviewHost(strings: Strings, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalStrings provides strings) {
        R1Theme { Box(Modifier.background(Tokens.Ground)) { content() } }
    }
}

private val summary = SettingsSummary(
    audio = AudioSettings(1000, eqEnabled = false, bands = emptyList(), eqMinMb = -1500, eqMaxMb = 1500),
    news = NewsSettings(enabled = true, time = "07:00", voice = "Lê Yến", categories = emptyList()),
    voice = "Adam bựa",
    bluetooth = BluetoothState(
        supported = true, enabled = true, scanning = false, autoReconnect = true, adapterName = "Phicomm_R1_2E2B",
        receiveSecondsLeft = 0, incomingAddress = "", connectedAddress = "", busy = "", error = "", devices = emptyList(),
    ),
)

@Preview
@Composable
private fun SettingsHomePreview() = PreviewHost(EnglishStrings) { SettingsHomeContent(Loadable.Loaded(summary), "10.25.113.209:8088", LanguageChoice.FollowPhone) {} }

@Preview
@Composable
private fun SettingsHomeLoadingPreview() = PreviewHost(EnglishStrings) { SettingsHomeContent(Loadable.Loading, "10.25.113.209:8088", LanguageChoice.English) {} }

@Preview
@Composable
private fun SettingsHomeVietnamesePreview() =
    PreviewHost(VietnameseStrings) { SettingsHomeContent(Loadable.Loaded(summary), "10.25.113.209:8088", LanguageChoice.FollowPhone) {} }
