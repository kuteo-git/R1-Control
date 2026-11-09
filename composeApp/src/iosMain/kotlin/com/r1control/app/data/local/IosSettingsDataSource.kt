package com.r1control.app.data.local

import platform.Foundation.NSUserDefaults

/** NSUserDefaults. `synchronize()` is deprecated and unnecessary, so it is not called. */
class IosSettingsDataSource : SettingsDataSource {

    private val defaults = NSUserDefaults.standardUserDefaults

    override suspend fun get(key: String): String? = defaults.stringForKey(key)

    override suspend fun set(key: String, value: String) =
        defaults.setObject(value, forKey = key)
}
