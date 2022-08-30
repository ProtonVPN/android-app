/*
 * Copyright (c) 2022. Proton AG
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

package com.protonvpn.android.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.protonvpn.android.logging.ProtonLogger
import com.protonvpn.android.logging.UiDisconnect
import com.protonvpn.android.logging.logUiSettingChange
import com.protonvpn.android.models.config.Setting
import com.protonvpn.android.models.config.UserData
import com.protonvpn.android.models.config.VpnProtocol
import com.protonvpn.android.models.profiles.Profile
import com.protonvpn.android.vpn.ProtocolSelection
import com.protonvpn.android.vpn.VpnConnectionManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class NotificationActionReceiver : BroadcastReceiver() {

    @Inject lateinit var vpnConnectionManager: VpnConnectionManager
    @Inject lateinit var notificationHelper: NotificationHelper
    @Inject lateinit var userData: UserData

    override fun onReceive(context: Context?, intent: Intent?) {
        when (intent?.action) {
            DISCONNECT_ACTION -> {
                ProtonLogger.log(UiDisconnect, "notification")
                vpnConnectionManager.disconnect("user via notification")
            }
            SMART_PROTOCOL_ACTION -> {
                val profileToSwitch = intent.getSerializableExtra(NotificationHelper.EXTRA_SWITCH_PROFILE) as Profile
                notificationHelper.cancelInformationNotification()
                ProtonLogger.logUiSettingChange(Setting.DEFAULT_PROTOCOL, "notification action")
                userData.protocol = ProtocolSelection(VpnProtocol.Smart)
                vpnConnectionManager.connectInBackground(profileToSwitch, "Enable Smart protocol from notification")
            }
        }
    }

    companion object {
        const val DISCONNECT_ACTION = "DISCONNECT_ACTION"
        const val SMART_PROTOCOL_ACTION = "SMART_PROTOCOL_ACTION"

        fun createIntent(context: Context, action: String) =
            Intent(context, NotificationActionReceiver::class.java).apply { this.action = action }
    }
}
