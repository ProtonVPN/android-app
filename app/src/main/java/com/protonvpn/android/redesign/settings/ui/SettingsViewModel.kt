/*
 * Copyright (c) 2023 Proton Technologies AG
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
package com.protonvpn.android.redesign.settings.ui

import android.annotation.TargetApi
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import com.protonvpn.android.BuildConfig
import com.protonvpn.android.R
import com.protonvpn.android.appconfig.AppFeaturesPrefs
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.auth.usecase.uiName
import com.protonvpn.android.components.InstalledAppsProvider
import com.protonvpn.android.managed.ManagedConfig
import com.protonvpn.android.netshield.NetShieldAvailability
import com.protonvpn.android.netshield.NetShieldProtocol
import com.protonvpn.android.netshield.getNetShieldAvailability
import com.protonvpn.android.redesign.recents.data.DefaultConnection
import com.protonvpn.android.redesign.recents.data.getRecentIdOrNull
import com.protonvpn.android.redesign.recents.usecases.RecentsManager
import com.protonvpn.android.redesign.vpn.ui.ConnectIntentPrimaryLabel
import com.protonvpn.android.redesign.vpn.ui.GetConnectIntentViewState
import com.protonvpn.android.redesign.vpn.usecases.SettingsForConnection
import com.protonvpn.android.settings.data.SplitTunnelingMode
import com.protonvpn.android.ui.settings.AppIconManager
import com.protonvpn.android.ui.settings.BuildConfigInfo
import com.protonvpn.android.ui.settings.CustomAppIconData
import com.protonvpn.android.utils.BuildConfigUtils
import com.protonvpn.android.utils.combine
import com.protonvpn.android.vpn.DnsOverride
import com.protonvpn.android.vpn.DnsOverrideFlow
import com.protonvpn.android.vpn.IsCustomDnsFeatureFlagEnabled
import com.protonvpn.android.vpn.ProtocolSelection
import com.protonvpn.android.vpn.VpnState
import com.protonvpn.android.vpn.VpnStatusProviderUI
import com.protonvpn.android.vpn.usecases.IsIPv6FeatureFlagEnabled
import com.protonvpn.android.widget.WidgetManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import me.proton.core.auth.domain.feature.IsFido2Enabled
import me.proton.core.auth.fido.domain.entity.Fido2RegisteredKey
import me.proton.core.domain.entity.UserId
import me.proton.core.user.domain.entity.UserRecovery
import me.proton.core.user.domain.extension.isCredentialLess
import me.proton.core.usersettings.domain.usecase.ObserveRegisteredSecurityKeys
import me.proton.core.usersettings.domain.usecase.ObserveUserSettings
import javax.inject.Inject
import me.proton.core.accountmanager.presentation.R as AccountManagerR
import me.proton.core.presentation.R as CoreR

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SettingsViewModel @Inject constructor(
    currentUser: CurrentUser,
    accountUserSettings: ObserveUserSettings,
    buildConfigInfo: BuildConfigInfo,
    settingsForConnection: SettingsForConnection,
    vpnStatusProviderUI: VpnStatusProviderUI,
    private val recentsManager: RecentsManager,
    private val installedAppsProvider: InstalledAppsProvider,
    private val getConnectIntentViewState: GetConnectIntentViewState,
    private val appIconManager: AppIconManager,
    private val managedConfig: ManagedConfig,
    private val isFido2Enabled: IsFido2Enabled,
    private val observeRegisteredSecurityKeys: ObserveRegisteredSecurityKeys,
    private val appWidgetManager: WidgetManager,
    private val appFeaturePrefs: AppFeaturesPrefs,
    private val isIPv6FeatureFlagEnabled: IsIPv6FeatureFlagEnabled,
    private val isCustomDnsFeatureFlagEnabled: IsCustomDnsFeatureFlagEnabled,
    val dnsOverrideFlow: DnsOverrideFlow,
) : ViewModel() {

    sealed class SettingViewState<T>(
        val value: T,
        val isRestricted: Boolean,
        @StringRes val titleRes: Int,
        val settingValueView: SettingValue?,
        @StringRes val descriptionRes: Int,
        @StringRes val annotationRes: Int? = null,
        @DrawableRes open val iconRes: Int? = null,
    ) {
        class NetShield(
            netShieldEnabled: Boolean,
            isRestricted: Boolean,
            overrideProfilePrimaryLabel: ConnectIntentPrimaryLabel.Profile?,
            val dnsOverride: DnsOverride,
            override val iconRes: Int = if (netShieldEnabled) R.drawable.feature_netshield_on else R.drawable.feature_netshield_off,
        ) : SettingViewState<Boolean>(
            value = netShieldEnabled,
            isRestricted = isRestricted,
            titleRes = R.string.netshield_feature_name,
            settingValueView = when {
                dnsOverride != DnsOverride.None -> {
                    SettingValue.SettingStringRes(R.string.netshield_state_unavailable)
                }
                else -> {
                    val subtitleRes =
                        if (netShieldEnabled) R.string.netshield_state_on else R.string.netshield_state_off
                    if (overrideProfilePrimaryLabel != null) {
                        SettingValue.SettingOverrideValue(
                            connectIntentPrimaryLabel = overrideProfilePrimaryLabel,
                            subtitleRes = subtitleRes
                        )
                    } else {
                        SettingValue.SettingStringRes(subtitleRes)
                    }
                }
            },
            descriptionRes = R.string.netshield_settings_description_not_html,
            annotationRes = R.string.learn_more
        )

        class SplitTunneling(
            isEnabled: Boolean,
            val mode: SplitTunnelingMode,
            val currentModeAppNames: List<CharSequence>,
            val currentModeIps: List<String>,
            isFreeUser: Boolean,
            override val iconRes: Int = if (isEnabled) R.drawable.feature_splittunneling_on else R.drawable.feature_splittunneling_off
        ) : SettingViewState<Boolean>(
            value = isEnabled,
            isRestricted = isFreeUser,
            titleRes = R.string.settings_split_tunneling_title,
            settingValueView = SettingValue.SettingStringRes(if (isEnabled) R.string.split_tunneling_state_on else R.string.split_tunneling_state_off),
            descriptionRes = R.string.settings_split_tunneling_description,
            annotationRes = R.string.learn_more
        )

        class VpnAccelerator(
            vpnAcceleratorSettingValue: Boolean,
            isFreeUser: Boolean,
            override val iconRes: Int = CoreR.drawable.ic_proton_rocket
        ) : SettingViewState<Boolean>(
            value = vpnAcceleratorSettingValue && !isFreeUser,
            isRestricted = isFreeUser,
            iconRes = CoreR.drawable.ic_proton_rocket,
            titleRes = R.string.settings_vpn_accelerator_title,
            settingValueView = SettingValue.SettingStringRes(if (vpnAcceleratorSettingValue && !isFreeUser) R.string.vpn_accelerator_state_on else R.string.vpn_accelerator_state_off),
            descriptionRes = R.string.settings_vpn_accelerator_description,
            annotationRes = R.string.learn_more
        )

        data class DefaultConnectionSettingState(
            val iconRes: Int = CoreR.drawable.ic_proton_bookmark,
            val titleRes: Int = R.string.settings_default_connection_title,
            val predefinedTitle: Int?,
            val recentLabel: ConnectIntentPrimaryLabel?,
        )
        class Protocol(
            protocol: ProtocolSelection,
            overrideProfilePrimaryLabel: ConnectIntentPrimaryLabel.Profile?,
            override val iconRes: Int = CoreR.drawable.ic_proton_servers,
        ) : SettingViewState<ProtocolSelection>(
            value = protocol,
            isRestricted = false,
            titleRes = R.string.settings_protocol_title,
            settingValueView =
                if (overrideProfilePrimaryLabel != null) {
                    SettingValue.SettingOverrideValue(
                        connectIntentPrimaryLabel = overrideProfilePrimaryLabel,
                        subtitleRes = protocol.displayName
                    )
                } else {
                    SettingValue.SettingStringRes(protocol.displayName)
                },
            descriptionRes = R.string.settings_protocol_description,
            annotationRes = R.string.learn_more
        )

        class AltRouting(enabled: Boolean) : SettingViewState<Boolean>(
            value = enabled,
            isRestricted = false,
            titleRes = R.string.settings_advanced_alternative_routing_title,
            settingValueView = null,
            descriptionRes = R.string.settings_advanced_alternative_routing_description,
        )

        class CustomDns(
            enabled: Boolean,
            val customDns: List<String>,
            overrideProfilePrimaryLabel: ConnectIntentPrimaryLabel.Profile?,
            isFreeUser: Boolean,
            val isPrivateDnsActive: Boolean,
        ) : SettingViewState<Boolean>(
            value = enabled,
            isRestricted = isFreeUser,
            titleRes = R.string.settings_custom_dns_title,
            settingValueView = when {
                isPrivateDnsActive ->
                    SettingValue.SettingStringRes(R.string.custom_dns_state_unavailable)

                overrideProfilePrimaryLabel != null ->
                    SettingValue.SettingOverrideValue(
                        connectIntentPrimaryLabel = overrideProfilePrimaryLabel,
                        subtitleRes = if (enabled) R.string.custom_dns_state_on else R.string.custom_dns_state_off
                    )

                else ->
                    SettingValue.SettingStringRes(
                        subtitleRes = if (enabled) R.string.custom_dns_state_on else R.string.custom_dns_state_off
                    )
            },
            descriptionRes = R.string.settings_advanced_dns_description,
            annotationRes = R.string.learn_more
        )

        class LanConnections(
            enabled: Boolean,
            isFreeUser: Boolean,
            overrideProfilePrimaryLabel: ConnectIntentPrimaryLabel.Profile?,
        ) : SettingViewState<Boolean>(
            value = enabled,
            isRestricted = isFreeUser,
            titleRes = R.string.settings_advanced_allow_lan_title,
            settingValueView =
                if (overrideProfilePrimaryLabel != null) {
                    SettingValue.SettingOverrideValue(
                        connectIntentPrimaryLabel = overrideProfilePrimaryLabel,
                        subtitleRes = if (enabled) R.string.lan_state_on else R.string.lan_state_off
                    )
                } else {
                    null
                },
            descriptionRes = R.string.settings_advanced_allow_lan_description,
        )

        class IPv6(enabled: Boolean) : SettingViewState<Boolean>(
            value = enabled,
            isRestricted = false,
            titleRes = R.string.settings_advanced_ipv6_title,
            settingValueView = null,
            descriptionRes = R.string.settings_advanced_ipv6_description,
        )

        class Nat(
            natType: NatType,
            isFreeUser: Boolean,
            overrideProfilePrimaryLabel: ConnectIntentPrimaryLabel.Profile?,
        ) : SettingViewState<NatType>(
            value = natType,
            isRestricted = isFreeUser,
            titleRes = R.string.settings_advanced_nat_type_title,
            settingValueView = if (overrideProfilePrimaryLabel != null) {
                SettingValue.SettingOverrideValue(
                    connectIntentPrimaryLabel = overrideProfilePrimaryLabel,
                    subtitleRes = natType.labelRes
                )
            } else {
                SettingValue.SettingStringRes(natType.labelRes)
            },
            descriptionRes = R.string.settings_advanced_nat_type_description,
            annotationRes = R.string.learn_more
        )
    }

    data class ProfileOverrideInfo(
        val primaryLabel: ConnectIntentPrimaryLabel.Profile,
        val profileName: String
    )

    data class SettingsViewState(
        val profileOverrideInfo: ProfileOverrideInfo? = null,
        val netShield: SettingViewState.NetShield?,
        val splitTunneling: SettingViewState.SplitTunneling,
        val vpnAccelerator: SettingViewState.VpnAccelerator,
        val defaultConnection: SettingViewState.DefaultConnectionSettingState? = null,
        val protocol: SettingViewState.Protocol,
        val altRouting: SettingViewState.AltRouting,
        val lanConnections: SettingViewState.LanConnections,
        val natType: SettingViewState.Nat,
        val ipV6: SettingViewState.IPv6?,
        val customDns: SettingViewState.CustomDns?,
        val buildInfo: String?,
        val showSignOut: Boolean,
        val showDebugTools: Boolean,
        val isWidgetDiscovered: Boolean,
        val accountScreenEnabled: Boolean,
        val versionName: String,
    )

    // The configuration doesn't change during runtime.
    private val displayDebugUi = BuildConfigUtils.displayDebugUi()
    private val buildConfigText = if (displayDebugUi) buildConfigInfo() else null

    val viewState =
        combine(
            currentUser.jointUserFlow,
            recentsManager.getDefaultConnectionFlow(),
            // Will return override settings if connected else global
            settingsForConnection.getFlowForCurrentConnection(),
            appFeaturePrefs.isWidgetDiscoveredFlow,
            isIPv6FeatureFlagEnabled.observe(),
            dnsOverrideFlow,
        ) { user, defaultConnection, connectionSettings, isWidgetDiscovered, isIPv6FeatureFlagEnabled, dnsOverride ->
            val isFree = user?.vpnUser?.isFreeUser == true
            val isCredentialLess = user?.user?.isCredentialLess() == true
            val settings = connectionSettings.connectionSettings
            val profileOverrideInfo = connectionSettings.associatedProfile?.let { profile ->
                val intentView = getConnectIntentViewState.forProfile(profile)
                ProfileOverrideInfo(
                    primaryLabel = intentView.primaryLabel,
                    profileName = profile.info.name
                )
            }
            val netShieldSetting = when (val netShieldAvailability = user?.vpnUser.getNetShieldAvailability()) {
                NetShieldAvailability.HIDDEN -> null
                else -> SettingViewState.NetShield(
                    settings.netShield != NetShieldProtocol.DISABLED,
                    overrideProfilePrimaryLabel = profileOverrideInfo?.primaryLabel,
                    isRestricted = netShieldAvailability != NetShieldAvailability.AVAILABLE,
                    dnsOverride = dnsOverride,
                )
            }
            val currentModeAppNames =
                installedAppsProvider.getNamesOfInstalledApps(settings.splitTunneling.currentModeApps())

            val defaultConnectionSetting = if (isFree)
                null
            else {
                val defaultRecent = defaultConnection.getRecentIdOrNull()?.let { recentsManager.getRecentById(it) }
                val recent = defaultRecent?.let { getConnectIntentViewState.forRecent(it, false) }
                SettingViewState.DefaultConnectionSettingState(
                    predefinedTitle = when (defaultConnection) {
                        DefaultConnection.LastConnection -> R.string.settings_last_connection_title
                        DefaultConnection.FastestConnection -> R.string.fastest_country
                        else -> null
                    },
                    recentLabel = recent?.primaryLabel,
                )
            }
            SettingsViewState(
                profileOverrideInfo = profileOverrideInfo,
                netShield = netShieldSetting,
                vpnAccelerator = SettingViewState.VpnAccelerator(settings.vpnAccelerator, isFree),
                splitTunneling = SettingViewState.SplitTunneling(
                    isEnabled = settings.splitTunneling.isEnabled,
                    mode = settings.splitTunneling.mode,
                    currentModeAppNames = currentModeAppNames,
                    currentModeIps = settings.splitTunneling.currentModeIps(),
                    isFreeUser = isFree,
                ),
                protocol = SettingViewState.Protocol(settings.protocol, profileOverrideInfo?.primaryLabel),
                defaultConnection = defaultConnectionSetting,
                altRouting = SettingViewState.AltRouting(settings.apiUseDoh),
                lanConnections = SettingViewState.LanConnections(settings.lanConnections, isFree, profileOverrideInfo?.primaryLabel),
                natType = SettingViewState.Nat(NatType.fromRandomizedNat(settings.randomizedNat), isFree, profileOverrideInfo?.primaryLabel),
                buildInfo = buildConfigText,
                showDebugTools = displayDebugUi,
                showSignOut = !isCredentialLess && !managedConfig.isManaged,
                accountScreenEnabled = !managedConfig.isManaged,
                isWidgetDiscovered = isWidgetDiscovered,
                customDns =
                    if (isCustomDnsFeatureFlagEnabled())
                        SettingViewState.CustomDns(
                            enabled = settings.customDns.enabled,
                            customDns = settings.customDns.rawDnsList,
                            overrideProfilePrimaryLabel = null,
                            isFreeUser = isFree,
                            isPrivateDnsActive = dnsOverride == DnsOverride.SystemPrivateDns,
                        )
                    else
                        null,
                versionName = BuildConfig.VERSION_NAME,
                ipV6 = if (isIPv6FeatureFlagEnabled) SettingViewState.IPv6(enabled = settings.ipV6Enabled) else null,
            )
        }

    val vpnAccelerator = viewState.map { it.vpnAccelerator }.distinctUntilChanged()
    val netShield = viewState.map { it.netShield }.distinctUntilChanged()
    private val profileOverrideInfo = viewState.map { it.profileOverrideInfo }.distinctUntilChanged()
    private val altRouting = viewState.map { it.altRouting }.distinctUntilChanged()
    private val lanConnections = viewState.map { it.lanConnections }.distinctUntilChanged()
    val natType = viewState.map { it.natType }.distinctUntilChanged()
    val ipv6 = viewState.map { it.ipV6 }.distinctUntilChanged()
    val protocol = viewState.map { it.protocol }.distinctUntilChanged()
    val customDns = viewState.map { it.customDns }.distinctUntilChanged()
    val splitTunneling = viewState.map { it.splitTunneling }.distinctUntilChanged()

    data class AdvancedSettingsViewState(
        val altRouting: SettingViewState.AltRouting,
        val lanConnections: SettingViewState.LanConnections,
        val natType: SettingViewState.Nat,
        val ipV6: SettingViewState.IPv6?,
        val customDns: SettingViewState.CustomDns?,
        val profileOverrideInfo: ProfileOverrideInfo? = null,
    )

    data class CustomDnsViewState(
        val dnsViewState: SettingViewState.CustomDns,
        val isConnected: Boolean
    ) {
        val showAddDnsButton get() =
            !dnsViewState.isPrivateDnsActive && (dnsViewState.customDns.isEmpty() || dnsViewState.value)
    }

    private val ipv6AndCustomDnsCombined = combine(
        ipv6,
        customDns
    ) { ipv6Enabled, customDns ->
        Pair(ipv6Enabled, customDns)
    }

    val customDnsViewState = combine(customDns, vpnStatusProviderUI.uiStatus) { customDns, status ->
        customDns?.let {
            CustomDnsViewState(dnsViewState = it, isConnected = status.state is VpnState.Connected)
        }
    }

    val advancedSettings = combine(
        altRouting,
        lanConnections,
        natType,
        profileOverrideInfo,
        ipv6AndCustomDnsCombined,
    ) { altRouting, lanConnections, natType, profileOverrideInfo, (ipv6, customDns) ->
        AdvancedSettingsViewState(
            altRouting = altRouting,
            lanConnections = lanConnections,
            natType = natType,
            profileOverrideInfo = profileOverrideInfo,
            ipV6 = ipv6,
            customDns = customDns,
        )
    }.distinctUntilChanged()

    data class AccountSettingsViewState(
        val userId: UserId, // Needed for navigating to activities
        val displayName: String,
        val planDisplayName: String?,
        val recoveryEmail: String?,
        @StringRes val passwordHint: Int?,
        val upgradeToPlusBanner: Boolean,
        val isFido2Enabled: Boolean,
        val registeredSecurityKeys: List<Fido2RegisteredKey>?
    )

    val accountSettings: Flow<AccountSettingsViewState?> = currentUser.jointUserFlow
        .filterNotNull()
        .flatMapLatest { (accountUser, vpnUser) ->
            combine(
                accountUserSettings(accountUser.userId),
                observeRegisteredSecurityKeys(accountUser.userId)
            ) { accountUserSettings, registeredSecurityKeys ->
                AccountSettingsViewState(
                    userId = accountUser.userId,
                    displayName = accountUser.uiName() ?: "",
                    planDisplayName = vpnUser.planDisplayName,
                    recoveryEmail = accountUserSettings?.email?.value,
                    passwordHint = accountUser.recovery?.state?.enum.passwordHint(),
                    upgradeToPlusBanner = vpnUser.isFreeUser,
                    isFido2Enabled = isFido2Enabled(accountUser.userId),
                    registeredSecurityKeys = registeredSecurityKeys
                )
            }
        }.distinctUntilChanged()

    fun getCurrentAppIcon() = appIconManager.getCurrentIconData()

    fun setNewAppIcon(newIcon: CustomAppIconData) = appIconManager.setNewAppIcon(newIcon)

    @TargetApi(26)
    fun onWidgetSettingClick(onNativeSelectionUnavailable: () -> Unit) {
        if (appWidgetManager.supportsNativeWidgetSelector()) {
            appWidgetManager.openNativeWidgetSelector()
        } else {
            onNativeSelectionUnavailable()
        }
        appFeaturePrefs.isWidgetDiscovered = true
    }
}

private fun UserRecovery.State?.passwordHint(): Int? = when(this) {
    null,
    UserRecovery.State.None,
    UserRecovery.State.Cancelled,
    UserRecovery.State.Expired -> null
    UserRecovery.State.Grace -> AccountManagerR.string.account_settings_list_item_password_hint_grace
    UserRecovery.State.Insecure -> AccountManagerR.string.account_settings_list_item_password_hint_insecure
}
