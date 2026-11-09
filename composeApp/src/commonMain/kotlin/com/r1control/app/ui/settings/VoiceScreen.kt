package com.r1control.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import org.jetbrains.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.r1control.app.domain.model.TtsVoices
import com.r1control.app.domain.repository.SpeakerException
import com.r1control.app.ui.Loadable
import com.r1control.app.ui.Problem
import com.r1control.app.ui.components.LoadableContent
import com.r1control.app.ui.components.ProblemBanner
import com.r1control.app.ui.components.R1Icon
import com.r1control.app.ui.components.SectionLabel
import com.r1control.app.ui.i18n.EnglishStrings
import com.r1control.app.ui.i18n.LocalStrings
import com.r1control.app.ui.i18n.Strings
import com.r1control.app.ui.i18n.VietnameseStrings
import com.r1control.app.ui.i18n.describe
import com.r1control.app.ui.icons.R1Icons
import com.r1control.app.ui.theme.R1Theme
import com.r1control.app.ui.theme.Space
import com.r1control.app.ui.theme.Tokens

@Composable
fun VoiceScreen(viewModel: VoiceViewModel) {
    val state by viewModel.state.collectAsState()
    VoiceContent(state, viewModel::choose, viewModel::load)
}

@Composable
fun VoiceContent(state: VoiceState, onChoose: (String) -> Unit, onRetry: () -> Unit) {
    val strings = LocalStrings.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = Space.XLarge)) {
        val problem = state.problem
        if (problem is Problem.Failed) {
            ProblemBanner(strings.describe(problem.error), Modifier.padding(horizontal = Space.Gutter, vertical = Space.Small))
        }
        LoadableContent(state.content, onRetry) { content ->
            when (content) {
                VoiceList.NoServer -> Text(
                    strings.voiceNoServer,
                    modifier = Modifier.padding(Space.Gutter),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                is VoiceList.Voices -> {
                    SectionLabel(strings.voiceAll, Modifier.padding(horizontal = Space.Gutter))
                    content.voices.available.forEach { name ->
                        VoiceRow(name, selected = name == content.voices.current, onClick = { onChoose(name) })
                    }
                }
            }
        }
    }
}

@Composable
internal fun VoiceRow(name: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = Space.Gutter),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            name,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
        )
        if (selected) R1Icon(R1Icons.Check, null, tint = MaterialTheme.colorScheme.primary)
        else Box(Modifier.size(24.dp))
    }
}

@Composable
private fun PreviewHost(strings: Strings, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalStrings provides strings) {
        R1Theme { Box(Modifier.background(Tokens.Ground)) { content() } }
    }
}

private val voices = TtsVoices("Lê Yến", listOf("Adam bựa", "Trúc Ly", "Thiện Minh", "Mai Anh", "Lê Yến", "Quốc Tuấn"))

@Preview
@Composable
private fun VoiceLoadingPreview() = PreviewHost(EnglishStrings) { VoiceContent(VoiceState(Loadable.Loading, Problem.None), {}, {}) }

@Preview
@Composable
private fun VoiceLoadedPreview() =
    PreviewHost(EnglishStrings) { VoiceContent(VoiceState(Loadable.Loaded(VoiceList.Voices(voices)), Problem.None), {}, {}) }

@Preview
@Composable
private fun VoiceNoServerPreview() =
    PreviewHost(EnglishStrings) { VoiceContent(VoiceState(Loadable.Loaded(VoiceList.NoServer), Problem.None), {}, {}) }

@Preview
@Composable
private fun VoiceFailedPreview() = PreviewHost(EnglishStrings) {
    VoiceContent(VoiceState(Loadable.Failed(SpeakerException.Unreachable(RuntimeException("x"))), Problem.None), {}, {})
}

@Preview
@Composable
private fun VoiceVietnamesePreview() =
    PreviewHost(VietnameseStrings) { VoiceContent(VoiceState(Loadable.Loaded(VoiceList.Voices(voices)), Problem.None), {}, {}) }
