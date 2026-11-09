package com.r1control.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextAlign
import org.jetbrains.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.r1control.app.domain.model.AudioSettings
import com.r1control.app.domain.model.EqBand
import com.r1control.app.domain.model.formatDb
import com.r1control.app.domain.model.formatHz
import com.r1control.app.domain.repository.SpeakerException
import com.r1control.app.ui.Loadable
import com.r1control.app.ui.Problem
import com.r1control.app.ui.components.LoadableContent
import com.r1control.app.ui.components.ProblemBanner
import com.r1control.app.ui.components.R1Slider
import com.r1control.app.ui.components.SwitchRow
import com.r1control.app.ui.i18n.EnglishStrings
import com.r1control.app.ui.i18n.LocalStrings
import com.r1control.app.ui.i18n.Strings
import com.r1control.app.ui.i18n.VietnameseStrings
import com.r1control.app.ui.i18n.describe
import com.r1control.app.ui.theme.R1Theme
import com.r1control.app.ui.theme.Space
import com.r1control.app.ui.theme.Tokens

class AudioActions(
    val onLoudness: (Int) -> Unit = {},
    val onLoudnessDone: () -> Unit = {},
    val onEq: (Boolean) -> Unit = {},
    val onBand: (Int, Int) -> Unit = { _, _ -> },
    val onBandDone: () -> Unit = {},
    val onRetry: () -> Unit = {},
)

@Composable
fun AudioScreen(viewModel: AudioViewModel) {
    val state by viewModel.state.collectAsState()
    AudioContent(
        state,
        AudioActions(
            viewModel::onLoudness, viewModel::onLoudnessDone, viewModel::onEqEnabled,
            viewModel::onBand, viewModel::onBandDone, viewModel::load,
        ),
    )
}

@Composable
fun AudioContent(state: AudioState, actions: AudioActions) {
    val strings = LocalStrings.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = Space.XLarge)) {
        val problem = state.problem
        if (problem is Problem.Failed) {
            ProblemBanner(strings.describe(problem.error), Modifier.padding(horizontal = Space.Gutter, vertical = Space.Small))
        }
        LoadableContent(state.audio, actions.onRetry) { audio ->
            // A title row, peer of the equaliser's switch row, with the value where a switch would be.
            val boost = formatDb(audio.loudnessMb, signed = true)
            Column(Modifier.padding(horizontal = Space.Gutter).padding(top = Space.Small)) {
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(strings.loudness, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    Text(boost, style = MaterialTheme.typography.bodyLarge)
                }
                R1Slider(
                    value = audio.loudnessMb.toFloat().coerceIn(AudioSettings.LOUDNESS_MIN_MB.toFloat(), AudioSettings.LOUDNESS_MAX_MB.toFloat()),
                    onValueChange = { actions.onLoudness(it.toInt()) },
                    valueRange = AudioSettings.LOUDNESS_MIN_MB.toFloat()..AudioSettings.LOUDNESS_MAX_MB.toFloat(),
                    onValueChangeFinished = actions.onLoudnessDone,
                    steps = (AudioSettings.LOUDNESS_MAX_MB - AudioSettings.LOUDNESS_MIN_MB) / AudioSettings.LOUDNESS_STEP_MB - 1,
                    description = strings.loudness,
                    valueText = boost,
                )
                Text(strings.loudnessHint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            SwitchRow(
                strings.equalizer, audio.eqEnabled, actions.onEq,
                modifier = Modifier.padding(top = Space.Large),
            )
            Column(Modifier.padding(horizontal = Space.Gutter)) {
                audio.bands.forEachIndexed { index, band ->
                    BandRow(
                        value = band.gainMb.toFloat(),
                        range = audio.eqMinMb.toFloat()..audio.eqMaxMb.toFloat(),
                        label = formatHz(band.freqHz),
                        valueText = formatDb(band.gainMb, signed = true),
                        onChange = { actions.onBand(index, it.toInt() / 50 * 50) },
                        onDone = actions.onBandDone,
                        enabled = audio.eqEnabled,
                    )
                }
            }
        }
    }
}

/** One equaliser band. Off, its words dim with its slider: the whole row is out of use, not just the thumb. */
@Composable
private fun BandRow(
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    label: String,
    valueText: String,
    onChange: (Float) -> Unit,
    onDone: () -> Unit,
    enabled: Boolean,
) {
    val words = Modifier.width(72.dp).alpha(if (enabled) 1f else 0.5f)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = words, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        R1Slider(
            value = value.coerceIn(range),
            onValueChange = onChange,
            valueRange = range,
            onValueChangeFinished = onDone,
            enabled = enabled,
            modifier = Modifier.weight(1f),
            description = label,
            valueText = valueText,
        )
        Text(valueText, modifier = words, textAlign = TextAlign.End, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun PreviewHost(strings: Strings, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalStrings provides strings) {
        R1Theme { Box(Modifier.background(Tokens.Ground)) { content() } }
    }
}

private val audio = AudioSettings(
    loudnessMb = 1000, eqEnabled = true,
    bands = listOf(EqBand(60, 1000), EqBand(230, 0), EqBand(910, -600), EqBand(3600, 0), EqBand(14000, 800)),
    eqMinMb = -1500, eqMaxMb = 1500,
)

@Preview
@Composable
private fun AudioLoadingPreview() = PreviewHost(EnglishStrings) { AudioContent(AudioState(Loadable.Loading, Problem.None), AudioActions()) }

@Preview
@Composable
private fun AudioLoadedPreview() = PreviewHost(EnglishStrings) { AudioContent(AudioState(Loadable.Loaded(audio), Problem.None), AudioActions()) }

@Preview
@Composable
private fun AudioEqOffPreview() =
    PreviewHost(EnglishStrings) { AudioContent(AudioState(Loadable.Loaded(audio.copy(eqEnabled = false)), Problem.None), AudioActions()) }

@Preview
@Composable
private fun AudioFailedPreview() = PreviewHost(EnglishStrings) {
    AudioContent(AudioState(Loadable.Failed(SpeakerException.Unreachable(RuntimeException("x"))), Problem.None), AudioActions())
}

@Preview
@Composable
private fun AudioVietnamesePreview() = PreviewHost(VietnameseStrings) { AudioContent(AudioState(Loadable.Loaded(audio), Problem.None), AudioActions()) }
