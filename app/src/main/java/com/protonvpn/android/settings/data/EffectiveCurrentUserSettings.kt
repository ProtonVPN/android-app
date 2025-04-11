/*
 * Copyright (c) 2023. Proton AG
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

package com.protonvpn.android.settings.data

import com.protonvpn.android.appconfig.Restrictions
import com.protonvpn.android.appconfig.RestrictionsConfig
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.concurrency.VpnDispatcherProvider
import com.protonvpn.android.netshield.NetShieldAvailability
import com.protonvpn.android.netshield.NetShieldProtocol
import com.protonvpn.android.netshield.getNetShieldAvailability
import com.protonvpn.android.tv.IsTvCheck
import com.protonvpn.android.utils.SyncStateFlow
import com.protonvpn.android.vpn.IsCustomDnsFeatureFlagEnabled
import com.protonvpn.android.vpn.usecases.IsDirectLanConnectionsFeatureFlagEnabled
import com.protonvpn.android.vpn.usecases.IsIPv6FeatureFlagEnabled
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Current user settings that are in effect.
 *
 * The effective value is based on the raw user setting but may be affected by additional constraints like feature
 * flags, paid vs free plan etc.
 */
@Singleton
class EffectiveCurrentUserSettings(
    mainScope: CoroutineScope,
    effectiveCurrentUserSettingsFlow: Flow<LocalUserSettings>
) {
    val effectiveSettings = effectiveCurrentUserSettingsFlow
        .distinctUntilChanged()
        .shareIn(mainScope, SharingStarted.Lazily, 1)

    val apiUseDoh = distinct { it.apiUseDoh }
    val defaultProfileId = distinct { it.defaultProfileId }
    val netShield = distinct { it.netShield }
    val protocol = distinct { it.protocol }
    val telemetry = distinct { it.telemetry }
    val vpnAccelerator = distinct { it.vpnAccelerator }
    val splitTunneling = distinct { it.splitTunneling }
    val ipV6Enabled = distinct { it.ipV6Enabled }
    val customDns = distinct { it.customDns }

    @Inject
    constructor(mainScope: CoroutineScope, effectiveCurrentUserSettingsFlow: EffectiveCurrentUserSettingsFlow)
        : this(mainScope, effectiveCurrentUserSettingsFlow as Flow<LocalUserSettings>)

    private fun <T> distinct(transform: (LocalUserSettings) -> T): Flow<T> =
        effectiveSettings.map(transform).distinctUntilChanged()
}

@Singleton
class EffectiveCurrentUserSettingsFlow constructor(
    rawCurrentUserSettingsFlow: Flow<LocalUserSettings>,
    currentUser: CurrentUser,
    isTv: IsTvCheck,
    restrictionFlow: Flow<Restrictions>,
    isIPv6FeatureFlagEnabled: IsIPv6FeatureFlagEnabled,
    isCustomDnsFeatureFlagEnabled: IsCustomDnsFeatureFlagEnabled,
    isDirectLanConnectionsFeatureFlagEnabled: IsDirectLanConnectionsFeatureFlagEnabled,
) : Flow<LocalUserSettings> {

    private data class Flags(
        val isIPv6Enabled: Boolean,
        val isCustomDnsEnabled: Boolean,
        val isDirectLanConnectionsEnabled: Boolean
    )
    private val flagsFlow = combine(
        isIPv6FeatureFlagEnabled.observe(),
        isCustomDnsFeatureFlagEnabled.observe(),
        isDirectLanConnectionsFeatureFlagEnabled.observe()
    ) { ipV6FeatureFlagEnabled, customDnsFeatureFlagEnabled, isDirectLanConnectionsFeatureFlagEnabled ->
        Flags(ipV6FeatureFlagEnabled, customDnsFeatureFlagEnabled, isDirectLanConnectionsFeatureFlagEnabled)
    }

    private val effectiveSettings: Flow<LocalUserSettings> = combine(
        rawCurrentUserSettingsFlow,
        currentUser.vpnUserFlow,
        restrictionFlow,
        flagsFlow,
    ) { settings, vpnUser, restrictions, flags ->
        val effectiveVpnAccelerator = restrictions.vpnAccelerator || settings.vpnAccelerator
        val netShieldAvailable = vpnUser.getNetShieldAvailability() == NetShieldAvailability.AVAILABLE
        val effectiveSplitTunneling = if (restrictions.splitTunneling)
            SplitTunnelingSettings(isEnabled = false) else settings.splitTunneling
        val lanConnections = isTv() || (!restrictions.lan && settings.lanConnections)
        settings.copy(
            defaultProfileId = if (!restrictions.quickConnect || isTv()) settings.defaultProfileId else null,
            lanConnections = lanConnections,
            lanConnectionsAllowDirect =
                lanConnections && settings.lanConnectionsAllowDirect && flags.isDirectLanConnectionsEnabled,
            netShield = if (netShieldAvailable) {
                if (isTv()) NetShieldProtocol.ENABLED else settings.netShield
            } else {
                NetShieldProtocol.DISABLED
            },
            customDns = if (flags.isCustomDnsEnabled && vpnUser?.isUserPlusOrAbove == true)
                settings.customDns
            else
                CustomDnsSettings(false),
            telemetry = settings.telemetry,
            vpnAccelerator = effectiveVpnAccelerator,
            splitTunneling = effectiveSplitTunneling,
            ipV6Enabled = settings.ipV6Enabled && flags.isIPv6Enabled && !isTv()
        )
    }

    @Inject
    constructor(
        localUserSettings: CurrentUserLocalSettingsManager,
        currentUser: CurrentUser,
        isTv: IsTvCheck,
        restrictions: RestrictionsConfig,
        isIPv6FeatureFlagEnabled: IsIPv6FeatureFlagEnabled,
        isCustomDnsEnabled: IsCustomDnsFeatureFlagEnabled,
        isDirectLanConnectionsFeatureFlagEnabled: IsDirectLanConnectionsFeatureFlagEnabled
    ) : this(localUserSettings.rawCurrentUserSettingsFlow, currentUser, isTv,
             restrictions.restrictionFlow, isIPv6FeatureFlagEnabled, isCustomDnsEnabled,
             isDirectLanConnectionsFeatureFlagEnabled)

    override suspend fun collect(collector: FlowCollector<LocalUserSettings>) = effectiveSettings.collect(collector)
}

@Deprecated(
    "Use EffectiveCurrentUserSettings.effectiveSettings flow, this object is for synchronous access in legacy code"
)
@Singleton
class EffectiveCurrentUserSettingsCached(
    private val stateFlow: StateFlow<LocalUserSettings>
) : StateFlow<LocalUserSettings> by stateFlow {

    @Inject constructor(
        mainScope: CoroutineScope,
        dispatcherProvider: VpnDispatcherProvider,
        effectiveCurrentUserSettingsFlow: EffectiveCurrentUserSettingsFlow
    ) : this(SyncStateFlow(mainScope, effectiveCurrentUserSettingsFlow, dispatcherProvider))
}
