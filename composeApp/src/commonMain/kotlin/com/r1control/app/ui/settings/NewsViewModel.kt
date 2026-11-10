package com.r1control.app.ui.settings

import com.r1control.app.domain.model.NewsSettings
import com.r1control.app.domain.model.NewsTime
import com.r1control.app.domain.model.moved
import com.r1control.app.domain.model.SpeakerSettings
import com.r1control.app.domain.repository.SpeakerException
import com.r1control.app.domain.repository.SpeakerRepository
import com.r1control.app.domain.repository.VoiceRepository
import com.r1control.app.domain.usecase.POLL_IDLE_MS
import com.r1control.app.domain.usecase.SpeakerView
import com.r1control.app.domain.usecase.snapshotOrNull
import com.r1control.app.ui.Held
import com.r1control.app.ui.HeldValue
import com.r1control.app.ui.Loadable
import com.r1control.app.ui.Problem
import com.r1control.app.ui.afterGoodRead
import com.r1control.app.ui.or
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class NewsAction { Idle, Testing, Tested }

data class NewsDraft(
    val news: NewsSettings,
    /** Voices the dropdown offers: the TTS server's, or just the current one when it cannot be asked. */
    val voices: List<String>,
)

data class NewsState(
    val draft: Loadable<NewsDraft>,
    val action: NewsAction,
    val problem: Problem,
)

/**
 * The bulletin's settings, as the R1 has them: read when the page opens and
 * every [pollMillis] while it is open. Every edit is sent at once, the whole
 * bulletin together as the R1 takes it: there is no draft to lose on the way
 * out. Writes go one at a time, and a write skips what the R1 already has, so
 * a run of taps ends with the last one sent.
 *
 * An edit is shown over the readings until [holdMillis] after the R1 took it;
 * one the R1 did not take lets go at once, so the page shows the R1's bulletin.
 */
class NewsViewModel(
    private val speaker: SpeakerRepository,
    private val voices: VoiceRepository,
    private val scope: CoroutineScope,
    private val pollMillis: Long = POLL_IDLE_MS,
    holdMillis: Long = 1_500,
    /** The feed's reading now: the page opens on its values, with no Loading frame, and the first read refreshes them. */
    start: SpeakerView = SpeakerView.Connecting,
    /** A save the speaker took: the shared reading is stale until it reads again. */
    private val onWrote: () -> Unit = {},
) {
    private val begun = start.snapshotOrNull()?.settings
    private val flow = MutableStateFlow(NewsState(Loadable.Loading, NewsAction.Idle, Problem.None))
    val state: StateFlow<NewsState> = flow.asStateFlow()
    private val writing = Mutex()
    private val edit = HeldValue<NewsSettings>(scope, holdMillis)
    private var reading: Loadable<NewsSettings> = begun?.let { Loadable.Loaded(it.news) } ?: Loadable.Loading
    private var available: List<String> = begun?.let { voices.remembered(it.ttsHost).available }.orEmpty()
    private var voicesAsked = false
    private var action = NewsAction.Idle
    private var readProblem: Problem = Problem.None
    private var writeProblem: Problem = Problem.None
    private var saved = begun?.news ?: NewsSettings(enabled = false, time = "", voice = "", categories = emptyList())

    // Readings apply in the order they started: a poll that left before a save must not land over the read after it.
    private var started = 0L
    private var applied = 0L

    init {
        show()
        scope.launch { edit.flow.collect { show() } }
        scope.launch {
            while (true) {
                read()
                delay(pollMillis)
            }
        }
    }

    /** The Try again under a failed first read. */
    fun load() {
        reading = Loadable.Loading
        voicesAsked = false
        show()
        scope.launch { read() }
    }

    fun onEnabled(enabled: Boolean) = edit { it.copy(enabled = enabled) }

    fun onHour(step: Int) = edit { it.copy(time = NewsTime.parse(it.time).plusHours(step).toString()) }

    fun onMinute(step: Int) = edit { it.copy(time = NewsTime.parse(it.time).plusMinutes(step).toString()) }

    fun onVoice(name: String) = edit { it.copy(voice = name) }

    fun onCategory(key: String, enabled: Boolean) = edit { news ->
        news.copy(categories = news.categories.map { if (it.key == key) it.copy(enabled = enabled) else it })
    }

    /**
     * The handle's release: one edit, so one save of the whole bulletin, on drop.
     * Categories this app has no label for are rows like any other and keep their place.
     */
    fun onMoved(from: Int, to: Int) = edit { news -> news.copy(categories = news.categories.moved(from, to)) }

    /**
     * Asks the R1 to read a bulletin now. It speaks: callers mind the volume.
     * Saved first even when nothing changed, as the panel does: the bulletin is
     * written by a server that only knows this speaker's topics once they have
     * been saved, and a test before that reads nothing.
     */
    fun test() {
        action = NewsAction.Testing
        writeProblem = Problem.None
        show()
        scope.launch {
            writing.withLock {
                try {
                    send(always = true)
                    speaker.testNews()
                    action = NewsAction.Tested
                } catch (e: CancellationException) {
                    throw e
                } catch (e: SpeakerException) {
                    action = NewsAction.Idle
                    writeProblem = Problem.Failed(e)
                }
                show()
            }
        }
    }

    private suspend fun send(always: Boolean) {
        val news = shown() ?: return
        if (!always && news == saved) {
            edit.answered(news, taken = true)
            return
        }
        try {
            speaker.saveNews(news)
        } catch (e: SpeakerException) {
            edit.answered(news, taken = false)
            throw e
        }
        saved = news
        edit.answered(news, taken = true)
        onWrote()
        scope.launch { read() }
    }

    private fun edit(change: (NewsSettings) -> NewsSettings) {
        val news = shown() ?: return
        edit.hold(change(news))
        action = NewsAction.Idle
        show()
        scope.launch {
            writing.withLock {
                writeProblem = try {
                    send(always = false)
                    Problem.None
                } catch (e: CancellationException) {
                    throw e
                } catch (e: SpeakerException) {
                    Problem.Failed(e)
                }
                show()
            }
        }
    }

    private suspend fun read() {
        val ticket = ++started
        try {
            val settings = speaker.settings()
            if (!voicesAsked) {
                voicesAsked = true
                available = voicesOf(settings).ifEmpty { available }
            }
            if (!applies(ticket)) return
            reading = Loadable.Loaded(settings.news)
            // A reading taken while an edit is held can be older than the edit's save.
            if (edit.flow.value is Held.Free) saved = settings.news
            readProblem = Problem.None
            writeProblem = writeProblem.afterGoodRead()
        } catch (e: CancellationException) {
            throw e
        } catch (e: SpeakerException) {
            if (!applies(ticket)) return
            if (reading is Loadable.Loaded) readProblem = Problem.Failed(e) else reading = Loadable.Failed(e)
        }
        show()
    }

    private suspend fun voicesOf(settings: SpeakerSettings): List<String> {
        if (!settings.hasTtsHost) return emptyList()
        return try {
            voices.voices(settings.ttsHost).available
        } catch (e: CancellationException) {
            throw e
        } catch (_: SpeakerException) {
            emptyList()
        }
    }

    private fun applies(ticket: Long): Boolean {
        if (ticket <= applied) return false
        applied = ticket
        return true
    }

    private fun shown(): NewsSettings? {
        val news = (reading as? Loadable.Loaded)?.value ?: return null
        return edit.flow.value.or(news)
    }

    private fun show() {
        val draft = when (val r = reading) {
            is Loadable.Loaded -> {
                val current = r.value.voice
                val offered = if (current.isNotEmpty() && current !in available) listOf(current) + available else available
                Loadable.Loaded(NewsDraft(edit.flow.value.or(r.value), offered))
            }
            Loadable.Loading -> Loadable.Loading
            is Loadable.Failed -> r
        }
        flow.value = NewsState(draft, action, if (writeProblem is Problem.Failed) writeProblem else readProblem)
    }
}
