package com.r1control.app.domain.repository

import com.r1control.app.domain.model.ha.HaTabs

/** The tabs layout this phone keeps: the app's own, edited by the person, never rebuilt from Home Assistant. */
interface HaLayoutRepository {
    /** The saved tabs, or null when none has ever been saved (or what was saved cannot be read). */
    suspend fun load(): HaTabs?

    suspend fun save(tabs: HaTabs)
}
