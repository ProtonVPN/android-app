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

import com.protonvpn.android.profiles.data.Profile
import com.protonvpn.android.profiles.usecases.GetProfileById
import com.protonvpn.android.redesign.recents.data.SettingsOverrides
import com.protonvpn.android.redesign.vpn.AnyConnectIntent
import com.protonvpn.android.settings.data.ApplyEffectiveUserSettings
import com.protonvpn.android.settings.data.ApplyEffectiveUserSettingsCached
import com.protonvpn.android.settings.data.CurrentUserLocalSettingsManager
import com.protonvpn.android.settings.data.EffectiveCurrentUserSettingsCached
import com.protonvpn.android.settings.data.LocalUserSettings
import com.protonvpn.android.vpn.VpnStatusProviderUI
import dagger.Reusable
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import me.proton.core.util.kotlin.DispatcherProvider
import javax.inject.Inject
import javax.inject.Singleton

@Reusable
class SettingsForConnection(
    private val rawSettingsFlow: Flow<LocalUserSettings>,
    private val getProfileById: GetProfileById,
    private val applyEffectiveUserSettings: ApplyEffectiveUserSettings,
    vpnStatusProviderUI: VpnStatusProviderUI,
) {
    data class CurrentConnectionSettings(
        val associatedProfile: Profile?,
        val connectionSettings: LocalUserSettings,
    )

    private val connectedIntentFlow = vpnStatusProviderUI.uiStatus
        .map { it.connectIntent }
        .distinctUntilChanged()

    @Inject
    constructor(
        settingsManager: CurrentUserLocalSettingsManager,
        getProfileById: GetProfileById,
        applyEffectiveUserSettings: ApplyEffectiveUserSettings,
        vpnStatusProviderUI: VpnStatusProviderUI,
    ) : this(
        rawSettingsFlow = settingsManager.rawCurrentUserSettingsFlow,
        getProfileById = getProfileById,
        applyEffectiveUserSettings = applyEffectiveUserSettings,
        vpnStatusProviderUI = vpnStatusProviderUI,
    )

    suspend fun getFor(intent: AnyConnectIntent?) : LocalUserSettings {
        val rawSettings = rawSettingsFlow.first()
        return applyEffectiveUserSettings(rawSettings.applyOverrides(intent?.settingsOverrides))
    }

    suspend fun fastGetFor(rawSettings: LocalUserSettings, intent: AnyConnectIntent?) : LocalUserSettings =
        // applyEffectiveUserSettings uses caching internally, so most executions are fast.
        applyEffectiveUserSettings(rawSettings.applyOverrides(intent?.settingsOverrides))

    @OptIn(ExperimentalCoroutinesApi::class)
    fun getFlowForCurrentConnection(): Flow<CurrentConnectionSettings> {
        return connectedIntentFlow.flatMapLatest { connectIntent ->
            getFlowForIntent(connectIntent)
        }
    }

    private fun getFlowForIntent(connectIntent: AnyConnectIntent?): Flow<CurrentConnectionSettings> {
        val profileId = connectIntent?.profileId
        val profileFlow = if (profileId == null) flowOf(null) else getProfileById.observe(profileId)
        return combine(
            profileFlow,
            rawSettingsFlow,
        ) { profile, rawSettings ->
            val effectiveConnectIntent = profile?.connectIntent ?: connectIntent
            CurrentConnectionSettings(
                associatedProfile = profile,
                connectionSettings = applyEffectiveUserSettings(
                    rawSettings.applyOverrides(effectiveConnectIntent?.settingsOverrides)
                )
            )
        }
    }
}

@Deprecated(
    "Use SettingsForConnection, this object is for synchronous access in legacy code"
)
@Singleton
class SettingsForConnectionSync @Inject constructor(
    private val dispatcherProvider: DispatcherProvider,
    // Technically raw settings are needed, but there is no cached version - effective settings work ok, the only
    // difference is that they go through ApplyEffectiveUserSettings one additional time.
    private val effectiveCurrentUserSettingsCached: EffectiveCurrentUserSettingsCached,
    private val applyEffectiveUserSettings: ApplyEffectiveUserSettingsCached
) {
    fun getForSync(
        intent: AnyConnectIntent,
    ) : LocalUserSettings =
        runBlocking(dispatcherProvider.Io) {
            val settingsWithIntentOverrides =
                effectiveCurrentUserSettingsCached.value.applyOverrides(intent.settingsOverrides)
            applyEffectiveUserSettings(settingsWithIntentOverrides)
        }
}

private fun LocalUserSettings.applyOverrides(overrides: SettingsOverrides?): LocalUserSettings {
    if (overrides == null) return this
    val lan = overrides.lanConnections ?: lanConnections
    return copy(
        netShield = overrides.netShield ?: netShield,
        lanConnections = lan,
        lanConnectionsAllowDirect = lan && overrides.lanConnectionsAllowDirect ?: lanConnectionsAllowDirect,
        randomizedNat = overrides.randomizedNat ?: randomizedNat,
        protocol = overrides.protocol ?: protocol,
        customDns = overrides.customDns ?: customDns
    )
}
