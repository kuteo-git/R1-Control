package com.r1control.app.domain.repository

import com.r1control.app.domain.model.LanguageChoice
import com.r1control.app.domain.model.Playlist
import com.r1control.app.domain.model.R1Address
import com.r1control.app.domain.model.RecentSearch
import com.r1control.app.domain.model.SavedAddress
import com.r1control.app.domain.model.StoredPlaylists

/** What this phone remembers between launches. */
interface PreferencesRepository {
    suspend fun address(): SavedAddress
    suspend fun saveAddress(address: R1Address)

    /** Newest first, at most `RECENT_SEARCH_CAP` (a longer stored list reads cut). */
    suspend fun recentSearches(): List<RecentSearch>
    suspend fun saveRecentSearches(searches: List<RecentSearch>)

    /**
     * Absent is `Readable(emptyList())`. A value that does not decode is
     * `Unreadable`, never an empty list: see [StoredPlaylists].
     */
    suspend fun playlists(): StoredPlaylists

    /** The whole list, newest first. Called only by `MusicStore`'s one save collector. */
    suspend fun savePlaylists(playlists: List<Playlist>)

    /** `FollowPhone` when nothing (or nothing readable) is stored. */
    suspend fun language(): LanguageChoice
    suspend fun saveLanguage(choice: LanguageChoice)
}
