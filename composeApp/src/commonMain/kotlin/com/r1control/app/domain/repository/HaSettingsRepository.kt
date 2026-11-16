package com.r1control.app.domain.repository

import com.r1control.app.domain.model.ha.HaAddress
import com.r1control.app.domain.model.ha.SavedHaAddress

/** The Home Assistant address and token this phone remembers. */
interface HaSettingsRepository {
    suspend fun saved(): SavedHaAddress
    suspend fun save(address: HaAddress)
}
