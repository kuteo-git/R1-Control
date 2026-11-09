package com.r1control.app.data.repository

import com.r1control.app.data.local.SettingsDataSource
import com.r1control.app.data.local.SettingsKeys
import com.r1control.app.data.remote.R1Json
import com.r1control.app.domain.model.AddressInput
import com.r1control.app.domain.model.LanguageChoice
import com.r1control.app.domain.model.Playlist
import com.r1control.app.domain.model.PlaylistId
import com.r1control.app.domain.model.R1Address
import com.r1control.app.domain.model.RECENT_SEARCH_CAP
import com.r1control.app.domain.model.RecentSearch
import com.r1control.app.domain.model.SavedAddress
import com.r1control.app.domain.model.SongList
import com.r1control.app.domain.model.StoredPlaylists
import com.r1control.app.domain.model.Track
import com.r1control.app.domain.model.cleanKeywords
import com.r1control.app.domain.repository.PreferencesRepository
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString

@Serializable
internal data class RecentSearchDto(val keywords: List<String> = emptyList(), val at: Long = 0)

/**
 * A playlist as stored on the phone. Every field defaults, like [RecentSearchDto],
 * so a later version that adds a field still reads this one and this one reads
 * a later one (`R1Json` ignores unknown keys).
 */
@Serializable
internal data class PlaylistDto(
    val id: String = "",
    val name: String = "",
    val songs: List<StoredTrackDto> = emptyList(),
    val created: Long = 0,
)

/** A song as stored. Its own shape, not the R1's wire `TrackDto`: a renamed R1 field must not make the phone's playlists unreadable. */
@Serializable
internal data class StoredTrackDto(
    val id: String = "",
    val title: String = "",
    val artist: String = "",
    val duration: String = "",
    val thumbnail: String = "",
)

internal const val LANGUAGE_FOLLOW = "follow"
internal const val LANGUAGE_VIETNAMESE = "vi"
internal const val LANGUAGE_ENGLISH = "en"

internal fun tokenOf(choice: LanguageChoice): String = when (choice) {
    LanguageChoice.FollowPhone -> LANGUAGE_FOLLOW
    LanguageChoice.Vietnamese -> LANGUAGE_VIETNAMESE
    LanguageChoice.English -> LANGUAGE_ENGLISH
}

class SettingsPreferencesRepository(
    private val settings: SettingsDataSource,
    private val nowMillis: () -> Long,
) : PreferencesRepository {

    override suspend fun address(): SavedAddress {
        val stored = settings.get(SettingsKeys.ADDRESS).orEmpty()
        return when (val input = R1Address.parse(stored)) {
            is AddressInput.Valid -> SavedAddress.Set(input.address)
            is AddressInput.Invalid -> SavedAddress.Unset
        }
    }

    override suspend fun saveAddress(address: R1Address) {
        settings.set(SettingsKeys.ADDRESS, address.toString())
    }

    /**
     * A stored list that no longer reads (an older shape, a half write) is an empty history, not a crash.
     * An entry with no keyword left (the DTO defaults a missing list to empty) is no search and is dropped.
     */
    override suspend fun recentSearches(): List<RecentSearch> {
        val stored = settings.get(SettingsKeys.RECENT_SEARCHES) ?: return emptyList()
        return try {
            R1Json.decodeFromString<List<RecentSearchDto>>(stored)
                .filter { cleanKeywords(it.keywords).isNotEmpty() }
                .map { RecentSearch(it.keywords, it.at) }
                .take(RECENT_SEARCH_CAP)
        } catch (_: SerializationException) {
            emptyList()
        } catch (_: IllegalArgumentException) {
            emptyList()
        }
    }

    override suspend fun saveRecentSearches(searches: List<RecentSearch>) {
        settings.set(
            SettingsKeys.RECENT_SEARCHES,
            R1Json.encodeToString(searches.map { RecentSearchDto(it.keywords, it.atMillis) }),
        )
    }

    override suspend fun playlists(): StoredPlaylists {
        val stored = settings.get(SettingsKeys.PLAYLISTS) ?: return StoredPlaylists.Readable(emptyList())
        val decoded = try {
            R1Json.decodeFromString<List<PlaylistDto>>(stored).mapNotNull(::toPlaylist)
        } catch (_: SerializationException) {
            return unreadable(stored)
        } catch (_: IllegalArgumentException) {
            return unreadable(stored)
        }
        return StoredPlaylists.Readable(decoded)
    }

    /**
     * An unreadable value is copied aside before anything may be saved over it.
     * The copy never takes an earlier backup's key, and only a copy that was
     * written lets the store start from an empty list.
     *
     * Copied once, not once per launch: the store never saves its empty start,
     * so the same value is read again at every start until a playlist is made.
     * Whether it is already kept is judged by content, against the copy the
     * last run recorded, so a run that stopped between the copy and the record
     * makes one more copy and the next run finds it.
     */
    private suspend fun unreadable(raw: String): StoredPlaylists.Unreadable = try {
        val kept = settings.get(SettingsKeys.PLAYLISTS_KEPT_AT)
        if (kept == null || settings.get(kept) != raw) {
            var at = nowMillis()
            while (settings.get(SettingsKeys.PLAYLISTS_BACKUP + at) != null) at++
            settings.set(SettingsKeys.PLAYLISTS_BACKUP + at, raw)
            settings.set(SettingsKeys.PLAYLISTS_KEPT_AT, SettingsKeys.PLAYLISTS_BACKUP + at)
        }
        StoredPlaylists.Unreadable(backedUp = true)
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        StoredPlaylists.Unreadable(backedUp = false)
    }

    override suspend fun savePlaylists(playlists: List<Playlist>) {
        settings.set(SettingsKeys.PLAYLISTS, R1Json.encodeToString(playlists.map(::toDto)))
    }

    override suspend fun language(): LanguageChoice = when (settings.get(SettingsKeys.LANGUAGE)) {
        LANGUAGE_VIETNAMESE -> LanguageChoice.Vietnamese
        LANGUAGE_ENGLISH -> LanguageChoice.English
        else -> LanguageChoice.FollowPhone
    }

    override suspend fun saveLanguage(choice: LanguageChoice) {
        settings.set(SettingsKeys.LANGUAGE, tokenOf(choice))
    }

    /**
     * An entry with no id is dropped (nothing could name it). A blank name stays
     * blank and `Playlist.hasName` says so. Songs with an empty id are dropped
     * and `SongList.of` drops repeats. That loses only what could never be shown
     * or played, and the first save writes the cleaned list.
     */
    private fun toPlaylist(dto: PlaylistDto): Playlist? {
        if (dto.id.isEmpty()) return null
        val songs = dto.songs.filter { it.id.isNotEmpty() }.map { Track(it.id, it.title, it.artist, it.duration, it.thumbnail) }
        return Playlist(PlaylistId(dto.id), dto.name, SongList.of(songs), dto.created)
    }

    private fun toDto(playlist: Playlist) = PlaylistDto(
        id = playlist.id.value,
        name = playlist.name,
        songs = playlist.songs.tracks.map { StoredTrackDto(it.id, it.title, it.artist, it.duration, it.thumbnailUrl) },
        created = playlist.createdAtMillis,
    )
}
