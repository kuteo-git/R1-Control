package com.r1control.app.data.local

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** SharedPreferences: two short strings, read at start-up and written when edited. */
class AndroidSettingsDataSource(context: Context) : SettingsDataSource {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("r1control", Context.MODE_PRIVATE)

    override suspend fun get(key: String): String? = withContext(Dispatchers.IO) {
        prefs.getString(key, null)
    }

    // commit(), not apply(): the caller is told the write is done when this
    // returns, and apply() would make that untrue for a force-quit right after.
    override suspend fun set(key: String, value: String) = withContext(Dispatchers.IO) {
        prefs.edit().putString(key, value).commit()
        Unit
    }
}
