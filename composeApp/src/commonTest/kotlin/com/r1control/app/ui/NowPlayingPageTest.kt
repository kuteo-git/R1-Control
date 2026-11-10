package com.r1control.app.ui

import com.r1control.app.domain.model.ListRef
import com.r1control.app.domain.model.MediaStatus
import com.r1control.app.domain.model.PlayOrder
import com.r1control.app.domain.model.Playing
import com.r1control.app.domain.usecase.MusicStore
import com.r1control.app.domain.usecase.SpeakerFeed
import com.r1control.app.domain.usecase.SpeakerView
import com.r1control.app.testing.FakeSpeaker
import com.r1control.app.testing.made
import com.r1control.app.testing.media
import com.r1control.app.testing.musicStore
import com.r1control.app.testing.speakerSnapshot
import com.r1control.app.testing.track
import com.r1control.app.testing.unreachable
import com.r1control.app.ui.music.NowPlayingPage
import com.r1control.app.ui.music.NowPlayingViewModel
import com.r1control.app.ui.music.PlaySource
import com.r1control.app.ui.music.nowPlayingPageOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class NowPlayingPageTest {
    private class Rig(scope: kotlinx.coroutines.CoroutineScope) {
        val speaker = FakeSpeaker()
        val feed = SpeakerFeed(speaker, scope)
        val store: MusicStore = musicStore(speaker, feed, scope)
        val vm = NowPlayingViewModel(store, feed, scope)

        fun reading(status: MediaStatus, id: String, vararg queue: String) {
            speaker.current = speakerSnapshot(media(status, id, title = "T$id", queue = queue.map { track(it) }))
            feed.refresh()
        }

        fun page() = nowPlayingPageOf(store.state.value, feed.view.value)
    }

    private fun TestScope.rig() = Rig(backgroundScope).also { runCurrent() }

    @Test fun aSpeakerNotOnMusicIsNothing() = runTest {
        val rig = rig()
        rig.reading(MediaStatus.Stopped, "", "a")
        runCurrent()
        assertEquals(NowPlayingPage.Nothing, rig.page())
        assertEquals(NowPlayingPage.Nothing, nowPlayingPageOf(rig.store.state.value, SpeakerView.Connecting))
        assertEquals(NowPlayingPage.Nothing, nowPlayingPageOf(rig.store.state.value, SpeakerView.Offline(unreachable)))
    }

    @Test fun aPlaylistTheAppSentIsFromAppNamedAfterIt() = runTest {
        val rig = rig()
        val id = rig.store.made("Nhạc xưa", listOf(track("a"), track("b"), track("c")))
        rig.store.play(ListRef.Saved(id), 1)
        rig.reading(MediaStatus.Playing, "b", "a", "b", "c")
        runCurrent()
        val page = assertIs<NowPlayingPage.FromApp>(rig.page())
        assertEquals(PlaySource.Playlist(id, "Nhạc xưa", hasName = true), page.source)
        assertEquals(listOf("a", "b", "c"), page.sequence.map { it.id })
        assertEquals("b", page.currentId)
        assertTrue(page.canReorder)
    }

    @Test fun aShuffledListIsInItsPlayingOrderAndCannotBeDragged() = runTest {
        val rig = rig()
        val id = rig.store.made("L", listOf(track("a"), track("b"), track("c"), track("d")))
        rig.store.shuffle(ListRef.Saved(id))
        val order = assertIs<PlayOrder.Shuffled>(assertIs<Playing.FromList>(rig.store.state.value.playing).order).ids
        rig.reading(MediaStatus.Playing, order[0], *order.toTypedArray())
        runCurrent()
        val page = assertIs<NowPlayingPage.FromApp>(rig.page())
        assertEquals(order, page.sequence.map { it.id })
        assertTrue(page.shuffled)
        assertEquals(false, page.canReorder)
    }

    @Test fun clearedResultsStillHaveTheirListWhileTheyPlay() = runTest {
        val rig = rig()
        val ref = rig.store.showResults(listOf(track("r1"), track("r2")))
        rig.store.play(ref, 0)
        rig.reading(MediaStatus.Playing, "r1", "r1", "r2")
        runCurrent()
        rig.store.showResults(emptyList())
        val page = assertIs<NowPlayingPage.FromApp>(rig.page())
        assertEquals(PlaySource.SearchResults, page.source)
        assertEquals(listOf("r1", "r2"), page.sequence.map { it.id })
    }

    @Test fun aSongThePhoneDidNotSendIsTheSpeakersOwnQueue() = runTest {
        val rig = rig()
        val id = rig.store.made("L", listOf(track("a"), track("b")))
        rig.store.play(ListRef.Saved(id), 0)
        rig.reading(MediaStatus.Playing, "voice1", "voice1", "voice2")
        runCurrent()
        val page = assertIs<NowPlayingPage.FromSpeaker>(rig.page())
        assertEquals(listOf("voice1", "voice2"), page.queue.map { it.id })
        assertEquals("voice1", page.currentId)
    }

    @Test fun aSongMissingFromTheSpeakersQueueIsStillNamed() = runTest {
        val rig = rig()
        rig.reading(MediaStatus.Playing, "x", "y")
        runCurrent()
        val page = assertIs<NowPlayingPage.FromSpeaker>(rig.page())
        assertEquals(listOf("x", "y"), page.queue.map { it.id })
        assertEquals("Tx", page.queue.first().title)
    }

    @Test fun aStaleReadingKeepsTheLastPage() = runTest {
        val rig = rig()
        val id = rig.store.made("L", listOf(track("a"), track("b")))
        rig.store.play(ListRef.Saved(id), 0)
        rig.reading(MediaStatus.Playing, "a", "a", "b")
        runCurrent()
        val online = assertIs<SpeakerView.Online>(rig.feed.view.value)
        val stale = SpeakerView.Stale(online.snapshot, unreachable)
        assertIs<NowPlayingPage.FromApp>(nowPlayingPageOf(rig.store.state.value, stale))
    }

    @Test fun aTappedRowPlaysFromItsPlaceInTheSameOrder() = runTest {
        val rig = rig()
        val id = rig.store.made("L", listOf(track("a"), track("b"), track("c"), track("d")))
        rig.store.shuffle(ListRef.Saved(id))
        val order = assertIs<PlayOrder.Shuffled>(assertIs<Playing.FromList>(rig.store.state.value.playing).order).ids
        rig.reading(MediaStatus.Playing, order[0], *order.toTypedArray())
        runCurrent()
        rig.speaker.calls.clear()
        rig.vm.onPlay(2)
        runCurrent()
        assertEquals("play $order from 2", rig.speaker.calls.first())
    }

    @Test fun listedRowsMoveAndRemoveThroughTheStoreButShuffledOnesDoNotMove() = runTest {
        val rig = rig()
        val id = rig.store.made("L", listOf(track("a"), track("b"), track("c")))
        rig.store.play(ListRef.Saved(id), 0)
        rig.reading(MediaStatus.Playing, "a", "a", "b", "c")
        runCurrent()
        rig.vm.onMove(0, 2)
        assertEquals(listOf("b", "c", "a"), rig.store.state.value.songs(ListRef.Saved(id)).tracks.map { it.id })
        rig.vm.onRemove(track("c"))
        assertEquals(listOf("b", "a"), rig.store.state.value.songs(ListRef.Saved(id)).tracks.map { it.id })
    }
}
