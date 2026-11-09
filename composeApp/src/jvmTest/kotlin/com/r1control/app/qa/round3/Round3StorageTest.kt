package com.r1control.app.qa.round3

import com.r1control.app.data.local.SettingsDataSource
import com.r1control.app.data.local.SettingsKeys
import com.r1control.app.data.repository.SettingsPreferencesRepository
import com.r1control.app.domain.model.Playlists
import com.r1control.app.domain.model.RecentSearch
import com.r1control.app.domain.usecase.MusicStore
import com.r1control.app.domain.usecase.SearchMusic
import com.r1control.app.domain.usecase.SpeakerFeed
import com.r1control.app.testing.made
import com.r1control.app.testing.FakeSpeaker
import com.r1control.app.testing.track
import com.r1control.app.ui.music.MusicViewModel
import com.r1control.app.ui.music.ShelfState
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The phone's own storage under the Music tab, end to end: the real
 * `SettingsPreferencesRepository` over a string map, as SharedPreferences and
 * NSUserDefaults hold it, with the store and the tab on top.
 */
class Round3StorageTest {
    private class Disk(val map: MutableMap<String, String> = mutableMapOf(), var failWrites: Boolean = false) : SettingsDataSource {
        override suspend fun get(key: String) = map[key]
        override suspend fun set(key: String, value: String) {
            if (failWrites) error("disk full")
            map[key] = value
        }

        val backups get() = map.keys.filter { it.startsWith(SettingsKeys.PLAYLISTS_BACKUP) }
    }

    private class Launch(val store: MusicStore, val music: MusicViewModel, val speaker: FakeSpeaker)

    /** One start of the app over [disk]. */
    private fun TestScope.launchOver(disk: Disk, clock: Long = 5_000L): Launch {
        val prefs = SettingsPreferencesRepository(disk) { clock }
        val speaker = FakeSpeaker()
        val feed = SpeakerFeed(speaker, backgroundScope)
        val store = MusicStore(speaker, prefs, feed, backgroundScope, Random(3)) { clock }
        val music = MusicViewModel(SearchMusic(speaker, prefs) { clock }, prefs, store, feed, backgroundScope)
        runCurrent()
        return Launch(store, music, speaker)
    }

    private fun twentyRecents() = (1..20).joinToString(",", "[", "]") { """{"keywords":["k$it"],"at":${100 - it}}""" }

    /** R3-46. A phone upgraded with 20 stored recents shows 10, and its next search keeps 10. */
    @Test fun twentyStoredRecentsShowTenAndTheNextSearchKeepsTen() = runTest {
        val disk = Disk(mutableMapOf(SettingsKeys.RECENT_SEARCHES to twentyRecents()))
        val app = launchOver(disk)
        val shown = app.music.state.value.recents
        assertEquals(10, shown.size)
        assertEquals((1..10).map { listOf("k$it") }, shown.map(RecentSearch::keywords), "the ten newest, newest first")

        app.speaker.searchAnswers = { listOf(track("x")) }
        app.music.onQuery("new one")
        app.music.search()
        advanceTimeBy(1_000)
        val after = app.music.state.value.recents
        assertEquals(10, after.size)
        assertEquals(listOf("new one"), after.first().keywords)
        assertTrue(listOf("k10") !in after.map(RecentSearch::keywords), "the oldest of the ten gives way")
        assertEquals(10, SettingsPreferencesRepository(disk) { 0 }.recentSearches().size)
    }

    /** R3-35. Unreadable playlists are copied aside, the shelf says so, and the first playlist made never touches the copy. */
    @Test fun unreadablePlaylistsAreKeptAsideAndTheFirstSaveLeavesTheCopyAlone() = runTest {
        val garbage = """[{"id":"p1","name":"Nhạc xưa","songs":"not a list"}]"""
        val disk = Disk(mutableMapOf(SettingsKeys.PLAYLISTS to garbage))
        val app = launchOver(disk)
        assertTrue(assertIs<ShelfState.Ready>(app.music.state.value.shelf).keptAside)
        assertEquals(listOf(garbage), disk.backups.map { disk.map.getValue(it) })
        assertEquals(garbage, disk.map[SettingsKeys.PLAYLISTS], "the empty start is not written")

        app.store.made("Mới", listOf(track("a")))
        runCurrent()
        assertTrue(disk.map.getValue(SettingsKeys.PLAYLISTS).contains("Mới"))
        assertEquals(listOf(garbage), disk.backups.map { disk.map.getValue(it) }, "the copy is still there, untouched")
    }

    /** R3-36. A backup that fails: the shelf is locked, and nothing the person does writes over the stored value. */
    @Test fun aFailedBackupLocksTheShelfAndNothingIsWrittenOverTheValue() = runTest {
        val garbage = "{oops"
        val disk = Disk(mutableMapOf(SettingsKeys.PLAYLISTS to garbage), failWrites = true)
        val app = launchOver(disk)
        assertEquals(ShelfState.Unreadable, app.music.state.value.shelf)
        disk.failWrites = false

        app.store.create("Mine", listOf(track("a")))
        app.music.onAddToPlaylist(listOf(track("b")))
        app.music.onPickNew("Other")
        runCurrent()
        assertEquals(Playlists.Unreadable, app.store.state.value.playlists)
        assertEquals(garbage, disk.map[SettingsKeys.PLAYLISTS])
        assertTrue(disk.backups.isEmpty())
    }

    /**
     * R3-38. The kept-aside start is never saved (it is empty), so the stored value stays unreadable and
     * every later launch copies the same value aside again: one more backup per start, without end.
     */
    @Test fun R3B_04_eachLaunchCopiesTheSameUnreadableValueAsideAgain() = runTest {
        val garbage = "{oops"
        val disk = Disk(mutableMapOf(SettingsKeys.PLAYLISTS to garbage))
        launchOver(disk, clock = 5_000L)
        launchOver(disk, clock = 9_000L)
        launchOver(disk, clock = 12_000L)
        assertEquals(1, disk.backups.size, "three starts left ${disk.backups.size} copies of one value: ${disk.backups}")
    }
}
