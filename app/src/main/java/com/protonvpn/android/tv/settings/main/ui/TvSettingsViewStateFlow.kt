/*
 * Copyright (c) 2026. Proton AG
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

package com.protonvpn.android.tv.settings.main.ui

import android.app.Activity
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import com.protonvpn.android.BuildConfig
import com.protonvpn.android.R
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.managed.ManagedConfig
import com.protonvpn.android.netshield.NetShieldAvailability
import com.protonvpn.android.netshield.getNetShieldAvailability
import com.protonvpn.android.redesign.settings.ui.customDnsStateRes
import com.protonvpn.android.redesign.settings.ui.netShieldStateRes
import com.protonvpn.android.settings.data.EffectiveCurrentUserSettings
import com.protonvpn.android.tv.reports.TvBugReportActivity
import com.protonvpn.android.tv.settings.IsTvAutoConnectFeatureFlagEnabled
import com.protonvpn.android.tv.settings.autoconnect.TvSettingsAutoConnectActivity
import com.protonvpn.android.tv.settings.customdns.TvSettingsCustomDnsActivity
import com.protonvpn.android.tv.settings.ipv6.TvSettingsIPv6Activity
import com.protonvpn.android.tv.settings.lanconnections.TvSettingsLanConnectionsActivity
import com.protonvpn.android.tv.settings.netshield.TvSettingsNetShieldActivity
import com.protonvpn.android.tv.settings.protocol.TvSettingsProtocolActivity
import com.protonvpn.android.tv.settings.splittunneling.TvSettingsSplitTunnelingActivity
import com.protonvpn.android.ui.settings.BuildConfigInfo
import com.protonvpn.android.utils.BuildConfigUtils
import com.protonvpn.android.utils.flatMapLatestNotNull
import com.protonvpn.android.vpn.IsPrivateDnsActiveFlow
import com.protonvpn.android.vpn.getDnsOverride
import dagger.Reusable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

data class TvSettingsViewState(
    val settings: List<TvSettingItem>,
    val accountEmail: String?,
    val planName: String?,
    val appVersion: String,
    val buildInfo: String? = null,
)

@Immutable
sealed interface TvSettingAction {
    object OpenUpsell : TvSettingAction
    object SignOut : TvSettingAction
    class OpenActivity(val activityClass: Class<out Activity>) : TvSettingAction
}

data class TvSettingItem(val setting: TvSetting, val value: TvSettingValue, val action: TvSettingAction?)

enum class TvSetting { NetShield, SplitTunneling, Protocol, AutoConnect, Lan, CustomDns, IPv6, ReportIssue, SignOut }

sealed interface TvSettingValue {
    data class Text(@param:StringRes val res: Int) : TvSettingValue
    data object None : TvSettingValue
    data object Restricted : TvSettingValue
}

@Reusable
class TvSettingsViewStateFlow @Inject constructor(
    private val currentUser: CurrentUser,
    private val effectiveSettings: EffectiveCurrentUserSettings,
    private val isPrivateDnsActiveFlow: IsPrivateDnsActiveFlow,
    private val isTvAutoConnectFeatureFlagEnabled: IsTvAutoConnectFeatureFlagEnabled,
    private val managedConfig: ManagedConfig,
    private val buildConfigInfo: BuildConfigInfo,
) {
    operator fun invoke(): Flow<TvSettingsViewState> = currentUser.jointUserFlow.flatMapLatestNotNull { user ->
        combine(
            effectiveSettings.effectiveSettings,
            isPrivateDnsActiveFlow,
            isTvAutoConnectFeatureFlagEnabled.observe(),
            managedConfig,
        ) { settings, isPrivateDnsActive, isAutoConnectEnabled, autoLoginConfig ->
            val vpnUser = user.vpnUser
            val isFree = vpnUser.isFreeUser
            val isNetShieldRestricted = vpnUser.getNetShieldAvailability() != NetShieldAvailability.AVAILABLE
            // Unlike mobile, credential-less users can sign out too.
            val showSignOut = autoLoginConfig == null

            fun tvSettingItem(setting: TvSetting, isRestricted: Boolean, valueRes: Int?): TvSettingItem {
                val value = when {
                   isRestricted -> TvSettingValue.Restricted
                   valueRes != null -> TvSettingValue.Text(valueRes)
                   else -> TvSettingValue.None
                }
                val action: TvSettingAction? = when {
                    isRestricted -> TvSettingAction.OpenUpsell
                    setting == TvSetting.SignOut -> TvSettingAction.SignOut
                    else -> setting.activityClass()?.let { TvSettingAction.OpenActivity(it) }
                }
                return TvSettingItem(setting, value, action)
            }

            val items = buildList {
                val dnsOverride = getDnsOverride(isPrivateDnsActive, settings)
                add(tvSettingItem(TvSetting.NetShield, isNetShieldRestricted, netShieldStateRes(settings.isNetShieldEnabled, dnsOverride)))

                add(
                    tvSettingItem(
                        TvSetting.SplitTunneling,
                        isFree,
                        if (settings.splitTunneling.isEnabled) R.string.split_tunneling_state_on else R.string.split_tunneling_state_off
                    )
                )

                add(tvSettingItem(TvSetting.Protocol, false, settings.protocol.displayName))
                if (isAutoConnectEnabled) {
                    add(
                        tvSettingItem(
                           TvSetting.AutoConnect,
                            false,
                            if (settings.tvAutoConnectOnBoot) R.string.auto_connect_state_on else R.string.auto_connect_state_off
                        )
                    )
                }
                add(
                    tvSettingItem(
                        TvSetting.Lan,
                        isFree,
                        if (settings.lanConnections) R.string.lan_state_on else R.string.lan_state_off
                    )
                )
                add(
                    tvSettingItem(
                        TvSetting.CustomDns,
                        isFree,
                        customDnsStateRes(settings.customDns.effectiveEnabled, isPrivateDnsActive)
                    )
                )
                add(
                    tvSettingItem(
                        TvSetting.IPv6,
                        false,
                        if (settings.ipV6Enabled) R.string.ipv6_state_on else R.string.ipv6_state_off
                    )
                )
                add(tvSettingItem(TvSetting.ReportIssue, false, null))
                if (showSignOut) add(tvSettingItem(TvSetting.SignOut, false, null))
            }
            TvSettingsViewState(
                settings = items,
                accountEmail = user.user.email,
                planName = vpnUser.planDisplayName,
                appVersion = BuildConfig.VERSION_NAME,
                buildInfo = getBuildInfo(),
            )
        }
    }

    private fun getBuildInfo() = if (BuildConfigUtils.displayDebugUi()) buildConfigInfo() else null

    private fun TvSetting.activityClass(): Class<out Activity>? = when (this) {
        TvSetting.NetShield -> TvSettingsNetShieldActivity::class.java
        TvSetting.SplitTunneling -> TvSettingsSplitTunnelingActivity::class.java
        TvSetting.Protocol -> TvSettingsProtocolActivity::class.java
        TvSetting.AutoConnect -> TvSettingsAutoConnectActivity::class.java
        TvSetting.Lan -> TvSettingsLanConnectionsActivity::class.java
        TvSetting.CustomDns -> TvSettingsCustomDnsActivity::class.java
        TvSetting.IPv6 -> TvSettingsIPv6Activity::class.java
        TvSetting.ReportIssue -> TvBugReportActivity::class.java
        TvSetting.SignOut -> null
    }
}
