package com.r1control.app.ui.settings

import com.r1control.app.domain.model.TtsVoices
import com.r1control.app.domain.repository.SpeakerException
import com.r1control.app.domain.repository.SpeakerRepository
import com.r1control.app.domain.repository.VoiceRepository
import com.r1control.app.domain.usecase.SpeakerView
import com.r1control.app.domain.usecase.snapshotOrNull
import com.r1control.app.ui.Loadable
import com.r1control.app.ui.Problem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface VoiceList {
    /** The R1 did not name a TTS server, so there is no list to ask. */
    data object NoServer : VoiceList
    data class Voices(val voices: TtsVoices) : VoiceList
}

data class VoiceState(val content: Loadable<VoiceList>, val problem: Problem)

class VoiceViewModel(
    private val speaker: SpeakerRepository,
    private val voices: VoiceRepository,
    private val scope: CoroutineScope,
    /** The reading the feed holds now: with a remembered list the page opens on it, and the read below refreshes it unseen. */
    start: SpeakerView = SpeakerView.Connecting,
) {
    private val known = start.snapshotOrNull()?.settings
    private val remembered = known?.let { voices.remembered(it.ttsHost) } ?: TtsVoices.None
    private val flow = MutableStateFlow(
        when {
            known == null -> VoiceState(Loadable.Loading, Problem.None)
            !known.hasTtsHost -> VoiceState(Loadable.Loaded(VoiceList.NoServer), Problem.None)
            remembered.isKnown -> VoiceState(Loadable.Loaded(VoiceList.Voices(remembered)), Problem.None)
            else -> VoiceState(Loadable.Loading, Problem.None)
        },
    )
    private var ttsHost = known?.ttsHost.orEmpty()
    /** The voice the server last took. */
    private var accepted = remembered.current
    private var latestChoice = 0
    private val sending = Mutex()

    val state: StateFlow<VoiceState> = flow.asStateFlow()

    init {
        read(visible = false)
    }

    /** The Try again under a failed read. */
    fun load() {
        flow.value = VoiceState(Loadable.Loading, Problem.None)
        read(visible = true)
    }

    /** Reads the list. A refresh of what is already shown replaces it unseen, and never over a choice made meanwhile. */
    private fun read(visible: Boolean) {
        scope.launch {
            val shown = try {
                val settings = speaker.settings()
                ttsHost = settings.ttsHost
                if (!settings.hasTtsHost) {
                    VoiceState(Loadable.Loaded(VoiceList.NoServer), Problem.None)
                } else {
                    val list = voices.voices(settings.ttsHost)
                    if (latestChoice == 0) accepted = list.current
                    VoiceState(Loadable.Loaded(VoiceList.Voices(list)), Problem.None)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: SpeakerException) {
                // A failed refresh leaves what is shown; only a first read has nothing to keep.
                if (flow.value.content is Loadable.Loaded) null else VoiceState(Loadable.Failed(e), Problem.None)
            }
            if (shown != null && (latestChoice == 0 || visible)) flow.value = shown
        }
    }

    /**
     * The check mark moves at once. Choices reach the server one at a time and
     * the last one wins: a choice overtaken while it waits is not sent, and a
     * refusal of an overtaken choice changes nothing on screen. A refusal of
     * the last choice moves the check back to the voice the server has.
     */
    fun choose(name: String) {
        val shown = shownVoices() ?: return
        if (shown.current == name) return
        val choice = ++latestChoice
        show(name, Problem.None)
        scope.launch {
            sending.withLock {
                if (choice != latestChoice) return@withLock
                try {
                    voices.setVoice(ttsHost, name)
                    accepted = name
                } catch (e: CancellationException) {
                    throw e
                } catch (e: SpeakerException) {
                    if (choice == latestChoice) show(accepted, Problem.Failed(e))
                }
            }
        }
    }

    private fun shownVoices(): TtsVoices? = ((flow.value.content as? Loadable.Loaded)?.value as? VoiceList.Voices)?.voices

    private fun show(current: String, problem: Problem) {
        val shown = shownVoices() ?: return
        flow.value = VoiceState(Loadable.Loaded(VoiceList.Voices(shown.copy(current = current))), problem)
    }
}
