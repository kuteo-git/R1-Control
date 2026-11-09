package com.r1control.app.data.local

/**
 * The few values this phone remembers between launches: a string-to-string
 * store with one implementation per platform (SharedPreferences, NSUserDefaults).
 * `suspend` so a later move to a file or a keychain changes no call site.
 */
interface SettingsDataSource {
    suspend fun get(key: String): String?
    suspend fun set(key: String, value: String)
}

object SettingsKeys {
    const val ADDRESS = "r1.address"
    const val RECENT_SEARCHES = "music.recentSearches"
    const val PLAYLISTS = "music.playlists"

    /** An unreadable [PLAYLISTS] value is copied to this key plus the time, and never overwritten. */
    const val PLAYLISTS_BACKUP = "music.playlists.backup."

    /** The key of the newest [PLAYLISTS_BACKUP], so the same unreadable value is not copied again at every start. */
    const val PLAYLISTS_KEPT_AT = "music.playlists.keptAt"
    const val LANGUAGE = "app.language"
}
