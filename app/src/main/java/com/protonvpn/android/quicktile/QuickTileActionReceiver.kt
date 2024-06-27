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
package com.protonvpn.android.quicktile

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.logging.ProtonLogger
import com.protonvpn.android.logging.UiConnect
import com.protonvpn.android.logging.UiDisconnect
import com.protonvpn.android.notifications.NotificationHelper
import com.protonvpn.android.redesign.app.ui.CreateLaunchIntent
import com.protonvpn.android.redesign.recents.usecases.GetQuickConnectIntent
import com.protonvpn.android.tv.IsTvCheck
import com.protonvpn.android.vpn.ConnectTrigger
import com.protonvpn.android.vpn.DisconnectTrigger
import com.protonvpn.android.vpn.VpnConnectionManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class QuickTileActionReceiver : BroadcastReceiver() {

    @Inject
    lateinit var vpnConnectionManager: VpnConnectionManager
    @Inject
    lateinit var isTv: IsTvCheck
    @Inject
    lateinit var currentUser: CurrentUser
    @Inject
    lateinit var mainScope: CoroutineScope
    @Inject
    lateinit var quickConnectIntent: GetQuickConnectIntent
    @Inject
    lateinit var notificationHelper: NotificationHelper
    @Inject
    lateinit var createLaunchIntent: CreateLaunchIntent

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_CONNECT -> {
                ProtonLogger.log(UiConnect, "quick tile")
                val pendingResult = goAsync()
                mainScope.launch {
                    if (currentUser.user() == null) {
                        context.startActivity(createLaunchIntent.forNotification(context))
                    } else {
                        vpnConnectionManager.connectInBackground(
                            quickConnectIntent(),
                            ConnectTrigger.QuickTile
                        )
                    }
                    pendingResult.finish()
                }
            }
            ACTION_DISCONNECT -> {
                ProtonLogger.log(UiDisconnect, "quick tile")
                vpnConnectionManager.disconnect(DisconnectTrigger.QuickTile)
            }
        }
    }

    companion object {
        const val ACTION_CONNECT = "ACTION_CONNECT"
        const val ACTION_DISCONNECT = "ACTION_DISCONNECT"
    }
}
