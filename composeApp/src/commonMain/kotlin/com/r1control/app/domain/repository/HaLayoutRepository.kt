package com.r1control.app.domain.repository

import com.r1control.app.domain.model.ha.HaDashboard

/** The dashboard layout this phone keeps: the app's own, edited by the person, never rebuilt from Home Assistant. */
interface HaLayoutRepository {
    /** The saved layout, or null when none has ever been saved (or what was saved cannot be read). */
    suspend fun load(): HaDashboard?

    suspend fun save(dashboard: HaDashboard)
}
