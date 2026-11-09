package com.r1control.app.ui.settings

import com.r1control.app.domain.model.AudioSettings
import com.r1control.app.domain.model.BluetoothState
import com.r1control.app.domain.model.NewsSettings
import com.r1control.app.domain.repository.SpeakerException
import com.r1control.app.domain.repository.SpeakerRepository
import com.r1control.app.domain.repository.VoiceRepository
import com.r1control.app.ui.Loadable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** What each settings row says under its title. */
data class SettingsSummary(
    val audio: AudioSettings,
    val news: NewsSettings,
    /** Empty when the R1 names no voice server or the server did not answer. */
    val voice: String,
    val bluetooth: BluetoothState,
)

/** Reads the current values once, each time the list is shown. */
class SettingsHomeViewModel(
    private val speaker: SpeakerRepository,
    private val voices: VoiceRepository,
    scope: CoroutineScope,
) {
    private val flow = MutableStateFlow<Loadable<SettingsSummary>>(Loadable.Loading)
    val state: StateFlow<Loadable<SettingsSummary>> = flow.asStateFlow()

    init {
        scope.launch {
            flow.value = try {
                val settings = speaker.settings()
                val bluetooth = speaker.bluetooth()
                val voice = if (!settings.hasTtsHost) "" else try {
                    voices.voices(settings.ttsHost).current
                } catch (e: CancellationException) {
                    throw e
                } catch (_: SpeakerException) {
                    ""
                }
                Loadable.Loaded(SettingsSummary(settings.audio, settings.news, voice, bluetooth))
            } catch (e: CancellationException) {
                throw e
            } catch (e: SpeakerException) {
                Loadable.Failed(e)
            }
        }
    }
}
