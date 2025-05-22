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
import com.protonvpn.android.settings.data.EffectiveCurrentUserSettings
import com.protonvpn.android.settings.data.EffectiveCurrentUserSettingsCached
import com.protonvpn.android.settings.data.LocalUserSettings
import com.protonvpn.android.utils.DebugUtils
import com.protonvpn.android.vpn.VpnStatusProviderUI
import com.protonvpn.android.vpn.usecases.IsDirectLanConnectionsFeatureFlagEnabled
import dagger.Reusable
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Reusable
class SettingsForConnection @Inject constructor(
    private val settings: EffectiveCurrentUserSettings,
    private val getProfileById: GetProfileById,
    private val isDirectLanConnectionsFeatureFlagEnabled: IsDirectLanConnectionsFeatureFlagEnabled,
    vpnStatusProviderUI: VpnStatusProviderUI,
) {
    suspend fun getFor(intent: AnyConnectIntent?) : LocalUserSettings =
        settings.effectiveSettings.first().applyEffectiveOverrides(intent?.settingsOverrides)

    private val connectedIntentFlow = vpnStatusProviderUI.uiStatus
        .map { it.connectIntent }
        .distinctUntilChanged()

    @OptIn(ExperimentalCoroutinesApi::class)
    fun getFlowForCurrentConnection(): Flow<CurrentConnectionSettings> {
        return connectedIntentFlow.flatMapLatest { connectIntent ->
            getFlowForIntent(connectIntent)
        }
    }

    private fun getFlowForIntent(connectIntent: AnyConnectIntent?): Flow<CurrentConnectionSettings> {
        val profileId = connectIntent?.profileId
        return if (profileId == null) {
                settings.effectiveSettings.map { effectiveSettings ->
                    CurrentConnectionSettings(
                        associatedProfile = null,
                        connectionSettings = effectiveSettings.applyEffectiveOverrides(connectIntent?.settingsOverrides)
                    )
                }
            } else {
                combine(
                    getProfileById.observe(profileId),
                    settings.effectiveSettings
                ) { profile, effectiveSettings ->
                    CurrentConnectionSettings(
                        associatedProfile = profile,
                        connectionSettings = effectiveSettings.applyEffectiveOverrides(profile?.connectIntent?.settingsOverrides)
                    )
                }
            }
        }

    private suspend fun LocalUserSettings.applyEffectiveOverrides(
        overrides: SettingsOverrides?
    ): LocalUserSettings {
        return applyEffectiveOverrides(
            overrides,
            isDirectLanConnectionsFeatureFlagEnabled = isDirectLanConnectionsFeatureFlagEnabled(),
        )
    }

    data class CurrentConnectionSettings(
        val associatedProfile: Profile?,
        val connectionSettings: LocalUserSettings
    )
}

@Deprecated(
    "Use SettingsForConnection, this object is for synchronous access in legacy code"
)
@Singleton
class SettingsForConnectionCached @Inject constructor(
    private val effectiveCurrentUserSettingsCached: EffectiveCurrentUserSettingsCached,
) {
    fun getFor(
        intent: AnyConnectIntent,
        isDirectLanConnectionsFeatureFlagEnabled: Boolean?,
    ) : LocalUserSettings {
        DebugUtils.debugAssert {
            isDirectLanConnectionsFeatureFlagEnabled != null
        }
        return effectiveCurrentUserSettingsCached.value.applyEffectiveOverrides(
            intent.settingsOverrides,
            isDirectLanConnectionsFeatureFlagEnabled = isDirectLanConnectionsFeatureFlagEnabled == true,
        )
    }
}

private fun LocalUserSettings.applyEffectiveOverrides(
    overrides: SettingsOverrides?,
    isDirectLanConnectionsFeatureFlagEnabled: Boolean
): LocalUserSettings {
    return applyOverrides(
        overrides?.copy(
            lanConnectionsAllowDirect =
                if (isDirectLanConnectionsFeatureFlagEnabled) overrides.lanConnectionsAllowDirect
                else null
        )
    )
}

fun LocalUserSettings.applyOverrides(overrides: SettingsOverrides?) : LocalUserSettings {
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
