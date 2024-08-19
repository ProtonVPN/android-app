/*
 * Copyright (c) 2024 Proton AG
 *
 * This file is part of ProtonVPN.
 *
 * ProtonVPN is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * ProtonVPN is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with ProtonVPN.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.protonvpn.android.redesign.vpn.usecases

import com.protonvpn.android.redesign.recents.data.SettingsOverrides
import com.protonvpn.android.redesign.vpn.AnyConnectIntent
import com.protonvpn.android.settings.data.EffectiveCurrentUserSettings
import com.protonvpn.android.settings.data.EffectiveCurrentUserSettingsCached
import com.protonvpn.android.settings.data.LocalUserSettings
import dagger.Reusable
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Reusable
class SettingsForConnection @Inject constructor(
    private val settings: EffectiveCurrentUserSettings
) {
    fun originalEffectiveSettings() = settings.effectiveSettings

    suspend fun getFor(intent: AnyConnectIntent?) : LocalUserSettings =
        settings.effectiveSettings.first().applyOverrides(intent?.settingsOverrides)
}

@Deprecated(
    "Use SettingsForConnection, this object is for synchronous access in legacy code"
)
@Singleton
class SettingsForConnectionCached @Inject constructor(
    private val effectiveCurrentUserSettingsCached: EffectiveCurrentUserSettingsCached
) {
    fun getFor(intent: AnyConnectIntent) : LocalUserSettings =
        effectiveCurrentUserSettingsCached.value.applyOverrides(intent.settingsOverrides)
}

fun LocalUserSettings.applyOverrides(overrides: SettingsOverrides?) : LocalUserSettings {
    if (overrides == null) return this
    return copy(
        netShield = overrides.netShield ?: netShield,
        lanConnections = overrides.lanConnections ?: lanConnections,
        randomizedNat = overrides.randomizedNat ?: randomizedNat,
        protocol = overrides.protocol ?: protocol
    )
}