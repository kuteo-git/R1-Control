package com.r1control.app

import com.r1control.app.data.local.SettingsDataSource
import com.r1control.app.data.remote.importDashboard
import com.r1control.app.data.remote.newHaClient
import com.r1control.app.data.remote.newHttpClient
import com.r1control.app.data.repository.HttpSpeakerRepository
import com.r1control.app.data.repository.HttpVoiceRepository
import com.r1control.app.data.repository.SettingsHaLayoutRepository
import com.r1control.app.data.repository.SettingsHaSettingsRepository
import com.r1control.app.data.repository.SettingsPreferencesRepository
import com.r1control.app.data.repository.WebSocketHaRepository
import com.r1control.app.domain.model.ha.HaDashboard
import com.r1control.app.domain.repository.HaLayoutRepository
import com.r1control.app.domain.repository.HaRepository
import com.r1control.app.domain.repository.HaSettingsRepository
import com.r1control.app.domain.repository.PreferencesRepository
import com.r1control.app.domain.repository.SpeakerRepository
import com.r1control.app.domain.usecase.ConnectHa
import com.r1control.app.domain.usecase.Foreground
import com.r1control.app.domain.usecase.ForegroundSpeaker
import com.r1control.app.domain.usecase.SaveAddress
import com.r1control.app.domain.usecase.SearchMusic
import com.r1control.app.domain.usecase.SendChat
import com.r1control.app.domain.usecase.VoiceCache
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlin.time.Clock

/**
 * The composition root: every object the app is made of, built once by hand.
 * No DI framework; a missing wire is a compile error here, not a runtime one.
 * Built once per process by each platform's entry point.
 */
class AppContainer(
    settings: SettingsDataSource,
    httpClient: HttpClient = newHttpClient(),
    haClient: HttpClient = newHaClient(),
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

    /** The Home Assistant link outlives every screen, so it runs in a scope of its own, on the main thread. */
    val haSettings: HaSettingsRepository = SettingsHaSettingsRepository(settings)
    val ha: HaRepository = WebSocketHaRepository(haClient, CoroutineScope(SupervisorJob() + Dispatchers.Main))
    val connectHa = ConnectHa(ha, haSettings)

    /** The dashboard the person builds: kept on the phone, and only ever seeded from Home Assistant's. */
    val haLayout: HaLayoutRepository = SettingsHaLayoutRepository(settings)

    /** Home Assistant's dashboard configuration becomes the app's dashboard here, at the wire, so the screens never see JSON. */
    val dashboardImporter: (String) -> HaDashboard = ::importDashboard
}
