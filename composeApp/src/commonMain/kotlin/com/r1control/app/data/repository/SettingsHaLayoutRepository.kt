package com.r1control.app.data.repository

import com.r1control.app.data.local.SettingsDataSource
import com.r1control.app.data.local.SettingsKeys
import com.r1control.app.domain.model.ha.HaDashboard
import com.r1control.app.domain.repository.HaLayoutRepository
import kotlinx.coroutines.CancellationException

/** The dashboard layout, as one JSON value in the same phone-local store as the rest. */
class SettingsHaLayoutRepository(private val settings: SettingsDataSource) : HaLayoutRepository {

    /**
     * An unreadable value is as good as none, but it is the person's work: it is copied aside once, so
     * the first layout seeded over it does not destroy it.
     */
    override suspend fun load(): HaDashboard? {
        val text = settings.get(SettingsKeys.HA_LAYOUT) ?: return null
        decodeLayout(text)?.let { return it }
        try {
            if (settings.get(SettingsKeys.HA_LAYOUT_BACKUP) == null) settings.set(SettingsKeys.HA_LAYOUT_BACKUP, text)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Nothing could be kept aside; the layout is still reported as unreadable.
        }
        return null
    }

    override suspend fun save(dashboard: HaDashboard) {
        settings.set(SettingsKeys.HA_LAYOUT, encodeLayout(dashboard))
    }
}
