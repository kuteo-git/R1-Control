package com.r1control.app.ui.settings

import com.r1control.app.domain.model.AudioSettings
import com.r1control.app.domain.model.BluetoothState
import com.r1control.app.domain.model.NewsSettings
import com.r1control.app.domain.repository.SpeakerException
import com.r1control.app.domain.repository.VoiceRepository
import com.r1control.app.domain.usecase.SpeakerFeed
import com.r1control.app.domain.usecase.SpeakerView
import com.r1control.app.domain.usecase.snapshotOrNull
import com.r1control.app.ui.Loadable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What each settings row says under its title. */
data class SettingsSummary(
    val audio: AudioSettings,
    val news: NewsSettings,
    /** Empty when the R1 names no voice server or the server did not answer. */
    val voice: String,
    val bluetooth: BluetoothState,
)

/**
 * The list's values come from the shared reading of the speaker, so it has them
 * on its first frame: it asks for no reading of its own. The voice name is the
 * voice server's, remembered from the last read and refreshed behind the frame.
 */
class SettingsHomeViewModel(
    private val feed: SpeakerFeed,
    private val voices: VoiceRepository,
    scope: CoroutineScope,
) {
    private val spoken = MutableStateFlow(rememberedName(feed.view.value))

    val state: StateFlow<Loadable<SettingsSummary>> = combine(feed.view, spoken) { view, voice -> summaryOf(view, voice) }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), summaryOf(feed.view.value, spoken.value))

    init {
        // The reading may be seconds old, and a page left a moment ago may have written to the speaker.
        feed.refresh()
        scope.launch {
            feed.view.map { it.snapshotOrNull()?.settings?.ttsHost.orEmpty() }
                .filter { it.isNotEmpty() }
                .distinctUntilChanged()
                .collectLatest { host ->
                    voices.remembered(host).takeIf { it.isKnown }?.let { spoken.value = it.current }
                    try {
                        spoken.value = voices.voices(host).current
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: SpeakerException) {
                        // The row keeps what it says.
                    }
                }
        }
    }

    private fun rememberedName(view: SpeakerView): String =
        view.snapshotOrNull()?.settings?.ttsHost?.let { voices.remembered(it).current }.orEmpty()
}

fun summaryOf(view: SpeakerView, voice: String): Loadable<SettingsSummary> = when (view) {
    SpeakerView.Connecting -> Loadable.Loading
    is SpeakerView.Offline -> Loadable.Failed(view.error)
    is SpeakerView.Online -> Loadable.Loaded(SettingsSummary(view.snapshot.settings.audio, view.snapshot.settings.news, voice, view.snapshot.bluetooth))
    is SpeakerView.Stale -> Loadable.Loaded(SettingsSummary(view.snapshot.settings.audio, view.snapshot.settings.news, voice, view.snapshot.bluetooth))
}
