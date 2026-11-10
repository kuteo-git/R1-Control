package com.r1control.app

import com.r1control.app.data.local.SettingsDataSource
import com.r1control.app.data.remote.newHttpClient
import com.r1control.app.data.repository.HttpSpeakerRepository
import com.r1control.app.data.repository.HttpVoiceRepository
import com.r1control.app.data.repository.SettingsPreferencesRepository
import com.r1control.app.domain.repository.PreferencesRepository
import com.r1control.app.domain.repository.SpeakerRepository
import com.r1control.app.domain.usecase.Foreground
import com.r1control.app.domain.usecase.ForegroundSpeaker
import com.r1control.app.domain.usecase.SaveAddress
import com.r1control.app.domain.usecase.SearchMusic
import com.r1control.app.domain.usecase.SendChat
import com.r1control.app.domain.usecase.VoiceCache
import io.ktor.client.HttpClient
import kotlin.time.Clock

/**
 * The composition root: every object the app is made of, built once by hand.
 * No DI framework; a missing wire is a compile error here, not a runtime one.
 * Built once per process by each platform's entry point.
 */
class AppContainer(
    settings: SettingsDataSource,
    httpClient: HttpClient = newHttpClient(),
) {
    private val nowMillis = { Clock.System.now().toEpochMilliseconds() }
    val preferences: PreferencesRepository = SettingsPreferencesRepository(settings, nowMillis)
    /** Written from the platform's lifecycle by the app shell; polled reads wait for it. */
    val foreground = Foreground()
    val speaker: SpeakerRepository = ForegroundSpeaker(HttpSpeakerRepository(httpClient, preferences), foreground)
    val voices = VoiceCache(HttpVoiceRepository(httpClient))

    val searchMusic = SearchMusic(speaker, preferences, nowMillis)
    val sendChat = SendChat(speaker)
    val saveAddress = SaveAddress(speaker, preferences)
}
