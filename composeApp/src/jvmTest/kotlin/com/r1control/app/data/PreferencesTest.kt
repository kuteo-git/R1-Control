package com.r1control.app.data

import com.r1control.app.data.local.SettingsDataSource
import com.r1control.app.data.local.SettingsKeys
import com.r1control.app.data.repository.SettingsPreferencesRepository
import com.r1control.app.domain.model.LanguageChoice
import com.r1control.app.domain.model.Playlist
import com.r1control.app.domain.model.PlaylistId
import com.r1control.app.domain.model.R1Address
import com.r1control.app.domain.model.RecentSearch
import com.r1control.app.domain.model.SavedAddress
import com.r1control.app.domain.model.SongList
import com.r1control.app.domain.model.StoredPlaylists
import com.r1control.app.domain.model.Track
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PreferencesTest {
    private class MemorySettings(val map: MutableMap<String, String> = mutableMapOf()) : SettingsDataSource {
        override suspend fun get(key: String) = map[key]
        override suspend fun set(key: String, value: String) { map[key] = value }
    }

    @Test fun addressRoundTripsAndStartsUnset() = runTest {
        val repo = SettingsPreferencesRepository(MemorySettings(), { 1_000L })
        assertEquals(SavedAddress.Unset, repo.address())
        repo.saveAddress(R1Address("192.168.1.20", 9000))
        assertEquals(SavedAddress.Set(R1Address("192.168.1.20", 9000)), repo.address())
    }

    @Test fun recentSearchesRoundTripWithAccents() = runTest {
        val repo = SettingsPreferencesRepository(MemorySettings(), { 1_000L })
        val searches = listOf(RecentSearch(listOf("Sương Trắng", "Thanh Ngân"), 1_700_000_000_000))
        repo.saveRecentSearches(searches)
        assertEquals(searches, repo.recentSearches())
    }

    /** T-10: a half-written entry read as no keywords was a recent search row that searched nothing. */
    @Test fun aStoredSearchWithNoKeywordsIsNotARecentSearch() = runTest {
        val stored = """[{"keywords":["bolero"],"at":2},{"at":1},{"keywords":["  "],"at":0}]"""
        val repo = SettingsPreferencesRepository(MemorySettings(mutableMapOf(SettingsKeys.RECENT_SEARCHES to stored)), { 1_000L })
        assertEquals(listOf(RecentSearch(listOf("bolero"), 2)), repo.recentSearches())
    }

    @Test fun aCorruptHistoryIsEmptyNotACrash() = runTest {
        val settings = MemorySettings(mutableMapOf(SettingsKeys.RECENT_SEARCHES to "{not json", SettingsKeys.ADDRESS to "???"))
        val repo = SettingsPreferencesRepository(settings, { 1_000L })
        assertEquals(emptyList(), repo.recentSearches())
        assertEquals(SavedAddress.Unset, repo.address())
    }

    @Test fun aStoredTwentyReadsAsTen() = runTest {
        val stored = (1..20).joinToString(",", "[", "]") { """{"keywords":["k$it"],"at":$it}""" }
        val repo = SettingsPreferencesRepository(MemorySettings(mutableMapOf(SettingsKeys.RECENT_SEARCHES to stored)), { 1_000L })
        val read = repo.recentSearches()
        assertEquals(10, read.size)
        assertEquals(listOf("k1"), read.first().keywords)
    }

    private val vietnamese = Track("qkXf2c5mlA8", "Thành Tâm Sám Hối", "NSƯT Thanh Ngân", "10:05", "https://yt3/x=w120")
    private val playlist = Playlist(
        PlaylistId("p1700000000000a1"), "Nhạc xưa",
        SongList.of(listOf(vietnamese, Track("b", "B", "", "", ""))), 1_700_000_000_000,
    )

    @Test fun playlistsRoundTripWithAccentsAndOrder() = runTest {
        val repo = SettingsPreferencesRepository(MemorySettings(), { 1_000L })
        assertEquals(StoredPlaylists.Readable(emptyList()), repo.playlists(), "a fresh install has none, and that is readable")
        val second = playlist.copy(id = PlaylistId("p2"), name = "Hai", songs = SongList.Empty)
        repo.savePlaylists(listOf(second, playlist))
        assertEquals(StoredPlaylists.Readable(listOf(second, playlist)), repo.playlists())
    }

    @Test fun aLaterVersionsExtraFieldsAndAnEarlierOnesMissingOnesStillRead() = runTest {
        val stored = """[{"id":"p1","name":"N","songs":[{"id":"a","title":"A","extra":1}],"created":5,"color":"red"},{"id":"p2"}]"""
        val repo = SettingsPreferencesRepository(MemorySettings(mutableMapOf(SettingsKeys.PLAYLISTS to stored)), { 1_000L })
        val read = assertIs<StoredPlaylists.Readable>(repo.playlists()).playlists
        assertEquals(listOf("N", ""), read.map { it.name })
        assertEquals("A", read.first().songs.tracks.single().title)
        assertTrue(!read.last().hasName)
    }

    @Test fun anEntryWithNoIdOrASongWithNoIdIsDroppedAndARepeatedSongCollapses() = runTest {
        val stored = """[{"name":"no id"},{"id":"p1","songs":[{"id":""},{"id":"a"},{"id":"a","title":"again"}]}]"""
        val repo = SettingsPreferencesRepository(MemorySettings(mutableMapOf(SettingsKeys.PLAYLISTS to stored)), { 1_000L })
        val read = assertIs<StoredPlaylists.Readable>(repo.playlists()).playlists
        assertEquals(listOf("p1"), read.map { it.id.value })
        assertEquals(listOf("a"), read.single().songs.tracks.map { it.id })
    }

    /** One bad read followed by one save must not delete every playlist: the raw value is kept aside first. */
    @Test fun anUnreadableValueIsCopiedAsideAndSaidSo() = runTest {
        val settings = MemorySettings(mutableMapOf(SettingsKeys.PLAYLISTS to "{not json"))
        val repo = SettingsPreferencesRepository(settings, { 1_234L })
        assertEquals(StoredPlaylists.Unreadable(backedUp = true), repo.playlists())
        assertEquals("{not json", settings.map[SettingsKeys.PLAYLISTS_BACKUP + 1_234L])
        assertEquals("{not json", settings.map[SettingsKeys.PLAYLISTS], "reading never rewrites the stored value")
    }

    @Test fun aBackupNeverTakesAnEarlierBackupsKey() = runTest {
        val settings = MemorySettings(mutableMapOf(
            SettingsKeys.PLAYLISTS to "[[[",
            SettingsKeys.PLAYLISTS_BACKUP + 1_234L to "the first",
        ))
        SettingsPreferencesRepository(settings, { 1_234L }).playlists()
        assertEquals("the first", settings.map[SettingsKeys.PLAYLISTS_BACKUP + 1_234L])
        assertEquals("[[[", settings.map[SettingsKeys.PLAYLISTS_BACKUP + 1_235L])
    }

    @Test fun whenTheCopyFailsTheAnswerSaysNothingWasKept() = runTest {
        val failing = object : SettingsDataSource {
            override suspend fun get(key: String) = if (key == SettingsKeys.PLAYLISTS) "nope" else null
            override suspend fun set(key: String, value: String) = error("disk full")
        }
        assertEquals(StoredPlaylists.Unreadable(backedUp = false), SettingsPreferencesRepository(failing, { 1L }).playlists())
    }

    @Test fun theLanguageChoiceRoundTripsAndAnythingUnknownFollowsThePhone() = runTest {
        val settings = MemorySettings()
        val repo = SettingsPreferencesRepository(settings, { 1L })
        assertEquals(LanguageChoice.FollowPhone, repo.language())
        for (choice in LanguageChoice.entries) {
            repo.saveLanguage(choice)
            assertEquals(choice, repo.language())
        }
        settings.map[SettingsKeys.LANGUAGE] = "klingon"
        assertEquals(LanguageChoice.FollowPhone, repo.language())
    }

    /** R3B-04. The same unreadable value at every start is copied once; a different one is copied again. */
    @Test fun anUnreadableValueAlreadyCopiedAsideIsNotCopiedAgain() = runTest {
        val settings = MemorySettings(mutableMapOf(SettingsKeys.PLAYLISTS to "{not json"))
        assertEquals(StoredPlaylists.Unreadable(backedUp = true), SettingsPreferencesRepository(settings, { 1_000L }).playlists())
        assertEquals(StoredPlaylists.Unreadable(backedUp = true), SettingsPreferencesRepository(settings, { 2_000L }).playlists())
        assertEquals(listOf("{not json"), settings.backups())

        settings.map[SettingsKeys.PLAYLISTS] = "[[["
        SettingsPreferencesRepository(settings, { 3_000L }).playlists()
        assertEquals(listOf("{not json", "[[["), settings.backups(), "a value that changed is a new one to keep")
    }

    private fun MemorySettings.backups() =
        map.filterKeys { it.startsWith(SettingsKeys.PLAYLISTS_BACKUP) }.toSortedMap().values.toList()
}
