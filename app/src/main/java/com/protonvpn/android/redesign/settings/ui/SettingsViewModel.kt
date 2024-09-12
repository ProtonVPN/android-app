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

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import com.protonvpn.android.R
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
import com.protonvpn.android.settings.data.EffectiveCurrentUserSettings
import com.protonvpn.android.settings.data.SplitTunnelingMode
import com.protonvpn.android.ui.settings.AppIconManager
import com.protonvpn.android.ui.settings.BuildConfigInfo
import com.protonvpn.android.ui.settings.CustomAppIconData
import com.protonvpn.android.utils.BuildConfigUtils
import com.protonvpn.android.vpn.ProtocolSelection
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

enum class NatType(
    val labelRes: Int,
    @StringRes val shortLabelRes: Int,
    @StringRes val descriptionRes: Int
) {
    Strict(
        labelRes = R.string.settings_advanced_nat_type_strict,
        shortLabelRes = R.string.settings_advanced_nat_type_strict_short,
        descriptionRes = R.string.settings_advanced_nat_type_strict_description
    ),
    Moderate(
        labelRes = R.string.settings_advanced_nat_type_moderate,
        shortLabelRes = R.string.settings_advanced_nat_type_moderate_short,
        descriptionRes = R.string.settings_advanced_nat_type_moderate_description
    );

    companion object {
        fun fromRandomizedNat(randomizedNat: Boolean) = if (randomizedNat) Strict else Moderate
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SettingsViewModel @Inject constructor(
    currentUser: CurrentUser,
    accountUserSettings: ObserveUserSettings,
    effectiveUserSettings: EffectiveCurrentUserSettings,
    buildConfigInfo: BuildConfigInfo,
    private val recentsManager: RecentsManager,
    private val installedAppsProvider: InstalledAppsProvider,
    private val getConnectIntentViewState: GetConnectIntentViewState,
    private val appIconManager: AppIconManager,
    private val managedConfig: ManagedConfig,
    private val isFido2Enabled: IsFido2Enabled,
    private val observeRegisteredSecurityKeys: ObserveRegisteredSecurityKeys
) : ViewModel() {

    sealed class SettingViewState<T>(
        val value: T,
        val isRestricted: Boolean,
        @StringRes val titleRes: Int,
        @StringRes val subtitleRes: Int,
        @StringRes val descriptionRes: Int,
        @StringRes val annotationRes: Int? = null,
        @DrawableRes open val iconRes: Int? = null,
        @DrawableRes val upgradeIconRes: Int? = if (isRestricted) R.drawable.vpn_plus_badge else null
    ) {
        class NetShield(
            netShieldEnabled: Boolean,
            isRestricted: Boolean,
            override val iconRes: Int = if (netShieldEnabled) R.drawable.feature_netshield_on else R.drawable.feature_netshield_off
        ) : SettingViewState<Boolean>(
            value = netShieldEnabled,
            isRestricted = isRestricted,
            upgradeIconRes = if (isRestricted) R.drawable.vpn_plus_badge else null,
            titleRes = R.string.netshield_feature_name,
            subtitleRes = if (netShieldEnabled) R.string.netshield_state_on else R.string.netshield_state_off,
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
            subtitleRes = if (isEnabled) R.string.split_tunneling_state_on else R.string.split_tunneling_state_off,
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
            subtitleRes = if (vpnAcceleratorSettingValue && !isFreeUser) R.string.vpn_accelerator_state_on else R.string.vpn_accelerator_state_off,
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
            override val iconRes: Int = CoreR.drawable.ic_proton_servers,
        ) : SettingViewState<ProtocolSelection>(
            value = protocol,
            isRestricted = false,
            titleRes = R.string.settings_protocol_title,
            subtitleRes = protocol.displayName,
            descriptionRes = R.string.settings_protocol_description,
            annotationRes = R.string.learn_more
        )

        class AltRouting(enabled: Boolean) : SettingViewState<Boolean>(
            value = enabled,
            isRestricted = false,
            titleRes = R.string.settings_advanced_alternative_routing_title,
            subtitleRes = if (enabled) R.string.alt_routing_state_on else R.string.alt_routing_state_off,
            descriptionRes = R.string.settings_advanced_alternative_routing_description,
        )

        class LanConnections(enabled: Boolean, isFreeUser: Boolean) : SettingViewState<Boolean>(
            value = enabled,
            isRestricted = isFreeUser,
            titleRes = R.string.settings_advanced_allow_lan_title,
            subtitleRes = if (enabled) R.string.lan_state_on else R.string.lan_state_off,
            descriptionRes = R.string.settings_advanced_allow_lan_description,
        )

        class Nat(natType: NatType, isFreeUser: Boolean) : SettingViewState<NatType>(
            value = natType,
            isRestricted = isFreeUser,
            titleRes = R.string.settings_advanced_nat_type_title,
            subtitleRes = natType.labelRes,
            descriptionRes = R.string.settings_advanced_nat_type_description,
            annotationRes = R.string.learn_more
        )
    }

    data class SettingsViewState(
        val netShield: SettingViewState.NetShield?,
        val splitTunneling: SettingViewState.SplitTunneling,
        val vpnAccelerator: SettingViewState.VpnAccelerator,
        val defaultConnection: SettingViewState.DefaultConnectionSettingState? = null,
        val protocol: SettingViewState.Protocol,
        val altRouting: SettingViewState.AltRouting,
        val lanConnections: SettingViewState.LanConnections,
        val natType: SettingViewState.Nat,
        val buildInfo: String?,
        val showSignOut: Boolean,
        val showDebugTools: Boolean,
        val accountScreenEnabled: Boolean,
    )

    // The configuration doesn't change during runtime.
    private val displayDebugUi = BuildConfigUtils.displayDebugUi()
    private val buildConfigText = if (displayDebugUi) buildConfigInfo() else null

    val viewState =
        combine(
            currentUser.jointUserFlow,
            // Keep in mind UI for some settings can't rely directly on effective settings.
            effectiveUserSettings.effectiveSettings,
            recentsManager.getDefaultConnectionFlow()
        ) { user, settings, defaultConnection ->
            val isFree = user?.vpnUser?.isFreeUser == true
            val isCredentialLess = user?.user?.isCredentialLess() == true
            val netShieldSetting = when (val netShieldAvailability = user?.vpnUser.getNetShieldAvailability()) {
                NetShieldAvailability.HIDDEN -> null
                else -> SettingViewState.NetShield(
                    settings.netShield != NetShieldProtocol.DISABLED,
                    isRestricted = netShieldAvailability != NetShieldAvailability.AVAILABLE
                )
            }
            val currentModeAppNames =
                installedAppsProvider.getNamesOfInstalledApps(settings.splitTunneling.currentModeApps())

            val defaultConnectionSetting = if (isFree)
                null
            else {
                val defaultRecent = defaultConnection.getRecentIdOrNull()?.let { recentsManager.getRecentById(it) }
                val recent = defaultRecent?.let { getConnectIntentViewState(it, false) }
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
                netShield = netShieldSetting,
                vpnAccelerator = SettingViewState.VpnAccelerator(settings.vpnAccelerator, isFree),
                splitTunneling = SettingViewState.SplitTunneling(
                    isEnabled = settings.splitTunneling.isEnabled,
                    mode = settings.splitTunneling.mode,
                    currentModeAppNames = currentModeAppNames,
                    currentModeIps = settings.splitTunneling.currentModeIps(),
                    isFreeUser = isFree,
                ),
                protocol = SettingViewState.Protocol(settings.protocol),
                defaultConnection = defaultConnectionSetting,
                altRouting = SettingViewState.AltRouting(settings.apiUseDoh),
                lanConnections = SettingViewState.LanConnections(settings.lanConnections, isFree),
                natType = SettingViewState.Nat(NatType.fromRandomizedNat(settings.randomizedNat), isFree),
                buildInfo = buildConfigText,
                showDebugTools = displayDebugUi,
                showSignOut = !isCredentialLess && !managedConfig.isManaged,
                accountScreenEnabled = !managedConfig.isManaged
            )
        }

    val vpnAccelerator = viewState.map { it.vpnAccelerator }.distinctUntilChanged()
    val netShield = viewState.map { it.netShield }.distinctUntilChanged()
    private val altRouting = viewState.map { it.altRouting }.distinctUntilChanged()
    private val lanConnections = viewState.map { it.lanConnections }.distinctUntilChanged()
    val natType = viewState.map { it.natType }.distinctUntilChanged()
    val protocol = viewState.map { it.protocol }.distinctUntilChanged()
    val splitTunneling = viewState.map { it.splitTunneling }.distinctUntilChanged()

    data class AdvancedSettingsViewState(
        val altRouting: SettingViewState.AltRouting,
        val lanConnections: SettingViewState.LanConnections,
        val natType: SettingViewState.Nat
    )
    val advancedSettings = combine(
        altRouting,
        lanConnections,
        natType
    ) { altRouting, lanConnections, natType ->
        AdvancedSettingsViewState(
            altRouting = altRouting,
            lanConnections = lanConnections,
            natType = natType
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

}

private fun UserRecovery.State?.passwordHint(): Int? = when(this) {
    null,
    UserRecovery.State.None,
    UserRecovery.State.Cancelled,
    UserRecovery.State.Expired -> null
    UserRecovery.State.Grace -> AccountManagerR.string.account_settings_list_item_password_hint_grace
    UserRecovery.State.Insecure -> AccountManagerR.string.account_settings_list_item_password_hint_insecure
}
