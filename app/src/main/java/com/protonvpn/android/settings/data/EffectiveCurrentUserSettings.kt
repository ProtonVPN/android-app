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

import com.protonvpn.android.auth.data.VpnUser
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.concurrency.VpnDispatcherProvider
import com.protonvpn.android.netshield.NetShieldAvailability
import com.protonvpn.android.netshield.NetShieldProtocol
import com.protonvpn.android.netshield.getNetShieldAvailability
import com.protonvpn.android.tv.IsTvCheck
import com.protonvpn.android.tv.settings.IsTvAutoConnectFeatureFlagEnabled
import com.protonvpn.android.tv.settings.IsTvCustomDnsSettingFeatureFlagEnabled
import com.protonvpn.android.tv.settings.IsTvNetShieldSettingFeatureFlagEnabled
import com.protonvpn.android.utils.SyncStateFlow
import com.protonvpn.android.utils.combine
import com.protonvpn.android.vpn.effectiveProtocol
import com.protonvpn.android.vpn.usecases.IsDirectLanConnectionsFeatureFlagEnabled
import com.protonvpn.android.vpn.usecases.IsIPv6FeatureFlagEnabled
import com.protonvpn.android.vpn.usecases.IsProTunV1FeatureFlagEnabled
import dagger.Reusable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import javax.inject.Inject
import javax.inject.Singleton

@Reusable
class SettingsFeatureFlagsFlow @Inject constructor(
    isIPv6FeatureFlagEnabled: IsIPv6FeatureFlagEnabled,
    isDirectLanConnectionsFeatureFlagEnabled: IsDirectLanConnectionsFeatureFlagEnabled,
    isTvAutoConnectFeatureFlagEnabled: IsTvAutoConnectFeatureFlagEnabled,
    isTvNetShieldSettingFeatureFlagEnabled: IsTvNetShieldSettingFeatureFlagEnabled,
    isTvCustomDnsSettingFeatureFlagEnabled: IsTvCustomDnsSettingFeatureFlagEnabled,
    isProTunV1FeatureFlagEnabled: IsProTunV1FeatureFlagEnabled
) : Flow<SettingsFeatureFlagsFlow.Flags> {

    data class Flags(
        val isIPv6Enabled: Boolean,
        val isDirectLanConnectionsEnabled: Boolean,
        val isTvAutoConnectEnabled: Boolean,
        val isTvNetShieldSettingEnabled: Boolean,
        val isTvCustomDnsSettingEnabled: Boolean,
        val isProTunV1Enabled: Boolean,
    )

    private val flow: Flow<Flags> = combine(
        isIPv6FeatureFlagEnabled.observe(),
        isDirectLanConnectionsFeatureFlagEnabled.observe(),
        isTvAutoConnectFeatureFlagEnabled.observe(),
        isTvNetShieldSettingFeatureFlagEnabled.observe(),
        isTvCustomDnsSettingFeatureFlagEnabled.observe(),
        isProTunV1FeatureFlagEnabled.observe()
    ) { isIPv6Enabled, isDirectLanConnectionsEnabled, isTvAutoConnectEnabled, isTvNetShieldEnabled, isTvCustomDnsEnabled, isProTunV1Enabled ->
        Flags(
            isIPv6Enabled = isIPv6Enabled,
            isDirectLanConnectionsEnabled = isDirectLanConnectionsEnabled,
            isTvAutoConnectEnabled = isTvAutoConnectEnabled,
            isTvNetShieldSettingEnabled = isTvNetShieldEnabled,
            isTvCustomDnsSettingEnabled = isTvCustomDnsEnabled,
            isProTunV1Enabled = isProTunV1Enabled,
        )
    }

    override suspend fun collect(collector: FlowCollector<Flags>) {
        flow.collect(collector)
    }
}

abstract class BaseApplyEffectiveUserSettings(
    protected val currentUser: CurrentUser,
    protected val isTv: IsTvCheck,
) {

    abstract suspend fun getFlags(): SettingsFeatureFlagsFlow.Flags
    abstract fun getFlagsFlow(): Flow<SettingsFeatureFlagsFlow.Flags>

    suspend operator fun invoke(rawSettings: LocalUserSettings): LocalUserSettings =
        applyRestrictions(rawSettings, currentUser.vpnUser(), isTv(), getFlags())

    fun observe(rawSettings: LocalUserSettings): Flow<LocalUserSettings> =
        combine(
            currentUser.vpnUserFlow,
            getFlagsFlow(),
        ) { vpnUser, flags ->
            applyRestrictions(rawSettings, vpnUser, isTv(), flags)
        }

    protected fun applyRestrictions(
        settings: LocalUserSettings,
        vpnUser: VpnUser?,
        isTv: Boolean,
        flags: SettingsFeatureFlagsFlow.Flags,
    ): LocalUserSettings {
        val isUserPlusOrAbove = vpnUser?.isUserPlusOrAbove == true
        val effectiveVpnAccelerator = !isUserPlusOrAbove || settings.vpnAccelerator
        val netShieldAvailable = vpnUser.getNetShieldAvailability() == NetShieldAvailability.AVAILABLE
        val effectiveSplitTunneling =
            if (isUserPlusOrAbove) settings.splitTunneling
            else SplitTunnelingSettings(isEnabled = false)
        val lanConnections = isUserPlusOrAbove && settings.lanConnections
        return settings.copy(
            defaultProfileId = if (isUserPlusOrAbove || isTv) settings.defaultProfileId else null,
            lanConnections = lanConnections,
            lanConnectionsAllowDirect =
                lanConnections && settings.lanConnectionsAllowDirect && flags.isDirectLanConnectionsEnabled,
            netShield = if (netShieldAvailable) {
                if (isTv && !flags.isTvNetShieldSettingEnabled) NetShieldProtocol.ENABLED else settings.netShield
            } else {
                NetShieldProtocol.DISABLED
            },
            customDns = if (isUserPlusOrAbove) settings.customDns else CustomDnsSettings(false),
            theme = settings.theme,
            tvAutoConnectOnBoot = if (isTv && flags.isTvAutoConnectEnabled) settings.tvAutoConnectOnBoot else false,
            vpnAccelerator = effectiveVpnAccelerator,
            splitTunneling = effectiveSplitTunneling,
            ipV6Enabled = settings.ipV6Enabled && flags.isIPv6Enabled,
            protocol = settings.protocol.effectiveProtocol(flags.isProTunV1Enabled),
        )
    }
}

@Reusable
class ApplyEffectiveUserSettings(
    mainScope: CoroutineScope,
    currentUser: CurrentUser,
    isTv: IsTvCheck,
    private val flags: Flow<SettingsFeatureFlagsFlow.Flags>
) : BaseApplyEffectiveUserSettings(currentUser, isTv) {

    private val cachedFlags = flags.shareIn(mainScope, SharingStarted.Lazily, 1)

    @Inject constructor(
        mainScope: CoroutineScope,
        currentUser: CurrentUser,
        isTv: IsTvCheck,
        flags: SettingsFeatureFlagsFlow
    ) : this(mainScope, currentUser, isTv, flags as Flow<SettingsFeatureFlagsFlow.Flags>)

    override suspend fun getFlags(): SettingsFeatureFlagsFlow.Flags = cachedFlags.first()

    override fun getFlagsFlow(): Flow<SettingsFeatureFlagsFlow.Flags> = cachedFlags
}

@Deprecated("Use ApplyEffectiveUserSettings, this object is for synchronous access in legacy code")
@Singleton
class ApplyEffectiveUserSettingsCached @Inject constructor(
    mainScope: CoroutineScope,
    currentUser: CurrentUser,
    isTv: IsTvCheck,
    flags: SettingsFeatureFlagsFlow
) : BaseApplyEffectiveUserSettings(currentUser, isTv) {

    private val cachedFlags = SyncStateFlow(mainScope, flags)

    override suspend fun getFlags(): SettingsFeatureFlagsFlow.Flags = cachedFlags.value // Non-suspending
    override fun getFlagsFlow(): Flow<SettingsFeatureFlagsFlow.Flags> = cachedFlags
}

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
    val tvAutoConnectOnBoot = distinct { it.tvAutoConnectOnBoot }
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

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class EffectiveCurrentUserSettingsFlow(
    rawCurrentUserSettingsFlow: Flow<LocalUserSettings>,
    applyEffectiveUserSettings: BaseApplyEffectiveUserSettings,
) : Flow<LocalUserSettings> {

    private val effectiveSettings: Flow<LocalUserSettings> =
        rawCurrentUserSettingsFlow.flatMapLatest(applyEffectiveUserSettings::observe)

    @Inject
    constructor(
        localUserSettings: CurrentUserLocalSettingsManager,
        applyEffectiveUserSettings: ApplyEffectiveUserSettings,
    ) : this(
        rawCurrentUserSettingsFlow = localUserSettings.rawCurrentUserSettingsFlow,
        applyEffectiveUserSettings = applyEffectiveUserSettings,
    )

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
        currentUserSettingsManager: CurrentUserLocalSettingsManager,
        applyEffectiveUserSettings: ApplyEffectiveUserSettingsCached,
    ) : this(
        SyncStateFlow(
            mainScope,
            EffectiveCurrentUserSettingsFlow(
                currentUserSettingsManager.rawCurrentUserSettingsFlow,
                applyEffectiveUserSettings
            ),
            dispatcherProvider
        )
    )
}
