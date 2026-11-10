package com.r1control.app.ui

import com.r1control.app.AppContainer
import com.r1control.app.domain.model.LanguageChoice
import com.r1control.app.domain.model.R1Address
import com.r1control.app.domain.model.SavedAddress
import com.r1control.app.domain.repository.PreferencesRepository
import com.r1control.app.domain.usecase.MusicStore
import com.r1control.app.domain.usecase.SpeakerFeed
import com.r1control.app.ui.chat.ChatViewModel
import com.r1control.app.ui.control.ControlViewModel
import com.r1control.app.ui.music.MiniPlayerViewModel
import com.r1control.app.ui.music.MusicViewModel
import com.r1control.app.ui.music.NowPlayingViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.random.Random
import kotlin.time.Clock

/** Whether the tabs can show yet: they need an address to ask. */
sealed interface Gate {
    data object Loading : Gate
    data object NeedsAddress : Gate
    data class Ready(val address: R1Address) : Gate
}

/**
 * What the app must know before it draws anything: the language and the
 * address. Both are read in one coroutine, the language first, and the gate
 * leaves [Gate.Loading] only after both, so the first frame is already in the
 * chosen language.
 */
class AppViewModel(
    private val preferences: PreferencesRepository,
    private val scope: CoroutineScope,
) {
    private val gateFlow = MutableStateFlow<Gate>(Gate.Loading)
    private val languageFlow = MutableStateFlow(LanguageChoice.FollowPhone)
    val gate: StateFlow<Gate> = gateFlow.asStateFlow()

    /** The one owner of the language choice. `FollowPhone` until read, which is never drawn: the gate is still Loading. */
    val language: StateFlow<LanguageChoice> = languageFlow.asStateFlow()

    init {
        scope.launch {
            try {
                languageFlow.value = preferences.language()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // An unreadable choice follows the phone.
            }
            gateFlow.value = try {
                when (val saved = preferences.address()) {
                    SavedAddress.Unset -> Gate.NeedsAddress
                    is SavedAddress.Set -> Gate.Ready(saved.address)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                Gate.NeedsAddress
            }
        }
    }

    /** Applies at once, so the app redraws in the new language, and is saved in the background. A failed save keeps the choice for this run. */
    fun chooseLanguage(choice: LanguageChoice) {
        languageFlow.value = choice
        scope.launch {
            try {
                preferences.saveLanguage(choice)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // The choice holds until the app is closed.
            }
        }
    }

    fun addressSaved(address: R1Address) {
        gateFlow.value = Gate.Ready(address)
    }
}

/** Everything that outlives a screen: built once, held by the activity's view model. */
class AppModels(container: AppContainer, scope: CoroutineScope) {
    val app = AppViewModel(container.preferences, scope)
    /** The voice list is read once, the first time the speaker names its voice server, so Settings opens knowing the voice. */
    val feed = SpeakerFeed(container.speaker, scope, onFresh = { container.voices.warm(it.settings.ttsHost, scope) })

    /** Built before the view models that read it: the only owner of song lists, the playing pointer and the one play request. */
    val store = MusicStore(
        container.speaker, container.preferences, feed, scope,
        random = Random.Default, nowMillis = { Clock.System.now().toEpochMilliseconds() },
    )
    val control = ControlViewModel(feed, container.speaker, store, scope)
    val music = MusicViewModel(container.searchMusic, container.preferences, store, feed, scope)
    val mini = MiniPlayerViewModel(feed, container.speaker, store, scope)
    val nowPlaying = NowPlayingViewModel(store, feed, scope)
    val chat = ChatViewModel(feed, container.sendChat, scope)

    /** A new address is a new speaker: the old one's reading, and what was sent to it, must not be shown as this one's. */
    fun addressSaved(address: R1Address) {
        feed.restart()
        store.speakerChanged()
        app.addressSaved(address)
    }
}
