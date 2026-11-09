package com.r1control.app

import android.content.Context
import com.r1control.app.data.local.AndroidSettingsDataSource

/**
 * The process-wide container, built on first use. Not an `Application`
 * subclass: that would be a manifest entry and a class for one field. It is
 * per process, not per Activity, so a rotation does not drop the HTTP client's
 * connection pool.
 */
object R1ControlApp {
    private var instance: AppContainer? = null

    fun container(context: Context): AppContainer = synchronized(this) {
        instance ?: AppContainer(
            settings = AndroidSettingsDataSource(context.applicationContext),
        ).also { instance = it }
    }
}
