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
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.protonvpn.android.R
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.auth.usecase.uiName
import com.protonvpn.android.components.InstalledAppsProvider
import com.protonvpn.android.netshield.NetShieldAvailability
import com.protonvpn.android.netshield.NetShieldProtocol
import com.protonvpn.android.netshield.getNetShieldAvailability
import com.protonvpn.android.settings.data.CurrentUserLocalSettingsManager
import com.protonvpn.android.settings.data.EffectiveCurrentUserSettings
import com.protonvpn.android.settings.data.SplitTunnelingSettings
import com.protonvpn.android.ui.settings.BuildConfigInfo
import com.protonvpn.android.userstorage.DontShowAgainStore
import com.protonvpn.android.utils.BuildConfigUtils
import com.protonvpn.android.vpn.ProtocolSelection
import com.protonvpn.android.vpn.VpnConnectionManager
import com.protonvpn.android.vpn.VpnStatusProviderUI
import com.protonvpn.android.vpn.VpnUiDelegate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import me.proton.core.domain.entity.UserId
import me.proton.core.presentation.savedstate.state
import me.proton.core.user.domain.entity.UserRecovery
import me.proton.core.user.domain.extension.isCredentialLess
import me.proton.core.usersettings.domain.usecase.ObserveUserSettings
import javax.inject.Inject
import me.proton.core.accountmanager.presentation.R as AccountManagerR
import me.proton.core.presentation.R as CoreR

enum class NatType(val labelRes: Int, val descriptionRes: Int) {
   Strict(
       labelRes = R.string.settings_advanced_nat_type_strict,
       descriptionRes = R.string.settings_advanced_nat_type_strict_description
   ),
   Moderate(
       labelRes = R.string.settings_advanced_nat_type_moderate,
       descriptionRes = R.string.settings_advanced_nat_type_moderate_description
   )
}

private const val ReconnectDialogStateKey = "reconnect_dialog"

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SettingsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    currentUser: CurrentUser,
    accountUserSettings: ObserveUserSettings,
    private val userSettingsManager: CurrentUserLocalSettingsManager,
    effectiveUserSettings: EffectiveCurrentUserSettings,
    buildConfigInfo: BuildConfigInfo,
    private val installedAppsProvider: InstalledAppsProvider,
    private val vpnConnectionManager: VpnConnectionManager,
    private val vpnStatusProviderUI: VpnStatusProviderUI,
    private val dontShowAgainStore: DontShowAgainStore
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
            val splitTunnelingSettings: SplitTunnelingSettings,
            val splitTunnelAppNames: List<String>,
            isFreeUser: Boolean,
            override val iconRes: Int = if (splitTunnelingSettings.isEnabled) R.drawable.feature_splittunneling_on else R.drawable.feature_splittunneling_off
        ) : SettingViewState<Boolean>(
            value = splitTunnelingSettings.isEnabled,
            isRestricted = isFreeUser,
            titleRes = R.string.settings_split_tunneling_title,
            subtitleRes = if (splitTunnelingSettings.isEnabled) R.string.split_tunneling_state_on else R.string.split_tunneling_state_off,
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
        val protocol: SettingViewState.Protocol,
        val altRouting: SettingViewState.AltRouting,
        val lanConnections: SettingViewState.LanConnections,
        val natType: SettingViewState.Nat,
        val buildInfo: String?,
        val showSignOut: Boolean,
    )

    // The configuration doesn't change during runtime.
    private val buildConfigText = if (BuildConfigUtils.displayInfo()) buildConfigInfo() else null

    private var showReconnectDialog by savedStateHandle.state<DontShowAgainStore.Type?>(null, ReconnectDialogStateKey)
    val showReconnectDialogFlow = savedStateHandle.getStateFlow<DontShowAgainStore.Type?>(ReconnectDialogStateKey, null)

    val viewState =
        combine(
            currentUser.jointUserFlow,
            // Keep in mind UI for some settings can't rely directly on effective settings.
            effectiveUserSettings.effectiveSettings,
        ) { user, settings ->
            val accountUser = user?.first
            val vpnUser = user?.second
            val isFree = vpnUser?.isFreeUser == true
            val isCredentialLess = accountUser?.isCredentialLess() == true
            val netShieldSetting = when (val netShieldAvailability = vpnUser.getNetShieldAvailability()) {
                NetShieldAvailability.HIDDEN -> null
                else -> SettingViewState.NetShield(
                    settings.netShield != NetShieldProtocol.DISABLED,
                    isRestricted = netShieldAvailability != NetShieldAvailability.AVAILABLE
                )
            }

            SettingsViewState(
                netShield = netShieldSetting,
                vpnAccelerator = SettingViewState.VpnAccelerator(settings.vpnAccelerator, isFree),
                splitTunneling = SettingViewState.SplitTunneling(
                    splitTunnelingSettings = settings.splitTunneling,
                    splitTunnelAppNames = installedAppsProvider.getNamesOfInstalledApps(settings.splitTunneling.excludedApps).map { it.toString() },
                    isFreeUser = isFree
                ),
                protocol = SettingViewState.Protocol(settings.protocol),
                altRouting = SettingViewState.AltRouting(settings.apiUseDoh),
                lanConnections = SettingViewState.LanConnections(settings.lanConnections, isFree),
                natType = SettingViewState.Nat(if (settings.randomizedNat) NatType.Strict else NatType.Moderate, isFree),
                buildInfo = buildConfigText,
                showSignOut = !isCredentialLess
            )
        }

    val vpnAccelerator = viewState.map { it.vpnAccelerator }.distinctUntilChanged()
    val netShield = viewState.map { it.netShield }.distinctUntilChanged()
    private val altRouting = viewState.map { it.altRouting }.distinctUntilChanged()
    private val lanConnections = viewState.map { it.lanConnections }.distinctUntilChanged()
    val natType = viewState.map { it.natType }.distinctUntilChanged()
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
    )

    val accountSettings: Flow<AccountSettingsViewState?> = currentUser.jointUserFlow
        .filterNotNull()
        .flatMapLatest { (accountUser, vpnUser) ->
            accountUserSettings(accountUser.userId).map { accountUserSettings ->
                AccountSettingsViewState(
                    userId = accountUser.userId,
                    displayName = accountUser.uiName() ?: "",
                    planDisplayName = vpnUser.planDisplayName,
                    recoveryEmail = accountUserSettings?.email?.value,
                    passwordHint = accountUser.recovery?.state?.enum.passwordHint(),
                    upgradeToPlusBanner = vpnUser.isFreeUser
                )
            }
        }.distinctUntilChanged()

    fun toggleNetShield() {
        viewModelScope.launch {
            userSettingsManager.toggleNetShield()
        }
    }

    fun updateProtocol(uiDelegate: VpnUiDelegate, newProtocol: ProtocolSelection) {
        viewModelScope.launch {
            userSettingsManager.update { current ->
                if (current.protocol != newProtocol) viewModelScope.launch {
                    reconnectionCheck(uiDelegate, DontShowAgainStore.Type.ProtocolChangeWhenConnected)
                }
                current.copy(protocol = newProtocol)
            }
        }
    }

    fun setNatType(type: NatType) {
        viewModelScope.launch {
            userSettingsManager.setRandomizedNat(type == NatType.Strict)
        }
    }

    fun toggleVpnAccelerator() {
        viewModelScope.launch {
            userSettingsManager.toggleVpnAccelerator()
        }
    }

    fun toggleAltRouting() {
        viewModelScope.launch {
            userSettingsManager.toggleAltRouting()
        }
    }

    fun toggleLanConnections(uiDelegate: VpnUiDelegate) {
        viewModelScope.launch {
            userSettingsManager.toggleLanConnections()
            reconnectionCheck(uiDelegate, DontShowAgainStore.Type.LanConnectionsChangeWhenConnected)
        }
    }

    fun toggleSplitTunneling(uiDelegate: VpnUiDelegate) {
        viewModelScope.launch {
            userSettingsManager.update { current ->
                val oldValue = current.splitTunneling
                val newValue = oldValue.copy(isEnabled = !oldValue.isEnabled)
                if (!oldValue.isEffectivelySameAs(newValue)) viewModelScope.launch {
                    reconnectionCheck(uiDelegate, DontShowAgainStore.Type.SplitTunnelingChangeWhenConnected)
                }
                current.copy(splitTunneling = newValue)
            }
        }
    }

    fun onSplitTunnelingUpdated(uiDelegate: VpnUiDelegate) {
        viewModelScope.launch {
            reconnectionCheck(uiDelegate, DontShowAgainStore.Type.SplitTunnelingChangeWhenConnected)
        }
    }

    private fun reconnect(uiDelegate: VpnUiDelegate) {
        vpnConnectionManager.reconnect("user via settings change", uiDelegate)
    }

    private suspend fun reconnectionCheck(uiDelegate: VpnUiDelegate, type: DontShowAgainStore.Type) {
        if (vpnStatusProviderUI.isEstablishingOrConnected) {
            when (dontShowAgainStore.getChoice(type)) {
                DontShowAgainStore.Choice.Positive -> reconnect(uiDelegate)
                DontShowAgainStore.Choice.Negative -> {} // No action
                DontShowAgainStore.Choice.ShowDialog -> showReconnectDialog = type
            }
        }
    }

    fun onReconnectClicked(uiDelegate: VpnUiDelegate, dontShowAgain: Boolean, type: DontShowAgainStore.Type) {
        showReconnectDialog = null
        viewModelScope.launch {
            if (dontShowAgain)
                dontShowAgainStore.setChoice(type, DontShowAgainStore.Choice.Positive)
            reconnect(uiDelegate)
        }
    }

    fun dismissReconnectDialog(dontShowAgain: Boolean, type: DontShowAgainStore.Type) {
        showReconnectDialog = null
        viewModelScope.launch {
            if (dontShowAgain)
                dontShowAgainStore.setChoice(type, DontShowAgainStore.Choice.Negative)
        }
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
