package com.r1control.app.ui.music

import com.r1control.app.domain.model.NowPlaying
import com.r1control.app.domain.repository.SpeakerRepository
import com.r1control.app.domain.usecase.MusicStore
import com.r1control.app.domain.usecase.SpeakerFeed
import com.r1control.app.domain.usecase.SpeakerView
import com.r1control.app.domain.usecase.Transport
import com.r1control.app.ui.CommandProblem
import com.r1control.app.ui.Problem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface MiniState {
    data object Hidden : MiniState
    data class Showing(
        val title: String,
        val artist: String,
        val coverUrl: String,
        val phase: NowPlaying.Phase,
        /** A play/pause that failed, or the speaker no longer answering. */
        val problem: Problem,
        val positionS: Int,
        val durationS: Int,
        /** `MusicStore.canNext`, the very flow the Control card draws its Next from. It is false over a song still downloading. */
        val canNext: Boolean,
    ) : MiniState {
        val playing: Boolean get() = phase == NowPlaying.Phase.Playing

        /** A song that is still downloading cannot be paused. */
        val canToggle: Boolean get() = phase !is NowPlaying.Phase.Downloading
    }
}

/** The small bar above the tab bar while music plays on the R1. */
class MiniPlayerViewModel(
    private val feed: SpeakerFeed,
    speaker: SpeakerRepository,
    private val store: MusicStore,
    private val scope: CoroutineScope,
) {
    private val transport = Transport(speaker)
    private val commandProblem = CommandProblem(feed, scope)

    val state: StateFlow<MiniState> = combine(feed.view, store.canNext, commandProblem.flow) { view, canNext, command ->
        val (snapshot, problem) = when (view) {
            is SpeakerView.Online -> view.snapshot to command
            is SpeakerView.Stale -> view.snapshot to Problem.Failed(view.error)
            else -> return@combine MiniState.Hidden
        }
        when (val now = snapshot.nowPlaying) {
            is NowPlaying.Music -> MiniState.Showing(now.title, now.artist, now.coverUrl, now.phase, problem, now.positionS, now.durationS, canNext)
            else -> MiniState.Hidden
        }
    }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), MiniState.Hidden)

    fun onPlayPause() {
        val shown = state.value as? MiniState.Showing ?: return
        scope.launch {
            commandProblem.report(transport.toggle(shown.phase))
            feed.refresh()
        }
    }

    /** The guard is a fast path: the store decides again at the press, from the latest reading. */
    fun onNext() {
        if ((state.value as? MiniState.Showing)?.canNext != true) return
        scope.launch {
            commandProblem.report(store.next())
            feed.refresh()
        }
    }
}
