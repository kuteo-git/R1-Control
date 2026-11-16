package com.r1control.app.data.repository

import com.r1control.app.data.local.SettingsDataSource
import com.r1control.app.data.local.SettingsKeys
import com.r1control.app.domain.model.ha.HaAddress
import com.r1control.app.domain.model.ha.HaInput
import com.r1control.app.domain.model.ha.SavedHaAddress
import com.r1control.app.domain.repository.HaSettingsRepository

/** Home Assistant's address and token, in the same phone-local store as the rest. */
class SettingsHaSettingsRepository(private val settings: SettingsDataSource) : HaSettingsRepository {

    /** A stored pair that no longer parses is as good as none: the form asks again. */
    override suspend fun saved(): SavedHaAddress {
        val address = settings.get(SettingsKeys.HA_ADDRESS).orEmpty()
        val token = settings.get(SettingsKeys.HA_TOKEN).orEmpty()
        return when (val input = HaAddress.parse(address, token)) {
            is HaInput.Valid -> SavedHaAddress.Set(input.address)
            is HaInput.Invalid -> SavedHaAddress.Unset
        }
    }

    override suspend fun save(address: HaAddress) {
        settings.set(SettingsKeys.HA_ADDRESS, address.hostAndPort)
        settings.set(SettingsKeys.HA_TOKEN, address.token)
    }
}
