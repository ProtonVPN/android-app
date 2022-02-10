/*
 * Copyright (c) 2021. Proton AG
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

package com.protonvpn.android.logging

import android.os.Build
import com.protonvpn.android.appconfig.AppConfig
import com.protonvpn.android.auth.data.VpnUser
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.models.config.Setting
import com.protonvpn.android.models.config.UserData
import com.protonvpn.android.models.config.VpnProtocol
import com.protonvpn.android.utils.ServerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingChangesLogger @Inject constructor(
    private val mainScope: CoroutineScope,
    private val currentUser: CurrentUser,
    private val serverManager: ServerManager,
    private val userData: UserData,
    private val appConfig: AppConfig
) {
    init {
        mainScope.launch {
            val currentVpnUser = currentUser.vpnUserFlow.stateIn(mainScope)
            userData.settingChangeEvent.collect { setting ->
                settingLogLine(setting, currentVpnUser.value)?.let { ProtonLogger.log(SettingsChanged, it) }
            }
        }
    }

    fun getCurrentSettingsForLog(vpnUser: VpnUser?) =
        Setting.values().mapNotNull { setting ->
            settingLogLine(setting, vpnUser)
        }.joinToString("\n")

    private fun settingLogLine(setting: Setting, vpnUser: VpnUser?): String? =
        settingLogValue(setting, vpnUser)?.let { "${setting.logName}: $it" }

    private fun settingLogValue(setting: Setting, vpnUser: VpnUser?): Any? = when (setting) {
        Setting.QUICK_CONNECT_PROFILE -> serverManager.defaultConnection.toLog(userData)
        Setting.DEFAULT_PROTOCOL -> protocolDescription(userData)
        Setting.NETSHIELD_PROTOCOL -> userData.getNetShieldProtocol(vpnUser)
        Setting.SECURE_CORE -> userData.secureCoreEnabled
        Setting.LAN_CONNECTIONS -> userData.shouldBypassLocalTraffic()
        Setting.SPLIT_TUNNEL_ENABLED -> userData.useSplitTunneling
        Setting.SPLIT_TUNNEL_IPS -> userData.splitTunnelIpAddresses.toLog()
        Setting.SPLIT_TUNNEL_APPS -> userData.splitTunnelApps.toLog()
        Setting.DEFAULT_MTU -> userData.mtuSize
        Setting.SAFE_MODE -> userData.safeModeEnabled ?: "default: " + appConfig.getFeatureFlags().safeMode
        Setting.API_DOH -> userData.apiUseDoH
        Setting.VPN_ACCELERATOR_ENABLED -> userData.vpnAcceleratorEnabled
        Setting.VPN_ACCELERATOR_NOTIFICATIONS -> userData.showVpnAcceleratorNotifications
        Setting.CONNECT_ON_BOOT ->
            userData.connectOnBoot.takeIf { Build.VERSION.SDK_INT < Build.VERSION_CODES.O }
    }

    private fun protocolDescription(userData: UserData): String {
        val transmission =
            if (userData.selectedProtocol == VpnProtocol.OpenVPN) userData.transmissionProtocol.toString()
            else ""
        return "${userData.selectedProtocol} $transmission"
    }

    private fun List<*>.toLog() = if (isEmpty()) "None" else joinToString()
}
