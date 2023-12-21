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

package com.protonvpn.android.components

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.redesign.recents.usecases.GetQuickConnectIntent
import com.protonvpn.android.settings.data.EffectiveCurrentUserSettings
import com.protonvpn.android.vpn.ConnectTrigger.Auto
import com.protonvpn.android.vpn.VpnConnectionManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject lateinit var mainScope: CoroutineScope
    @Inject lateinit var userSettings: EffectiveCurrentUserSettings
    @Inject lateinit var currentVpnUser: CurrentUser
    @Inject lateinit var vpnConnectionManager: VpnConnectionManager
    @Inject lateinit var quickConnectIntent: GetQuickConnectIntent

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != "android.intent.action.BOOT_COMPLETED") return

        val pendingResult = goAsync()
        mainScope.launch {
            try {
                val settings = userSettings.effectiveSettings.first()
                val isLoggedIn = currentVpnUser.isLoggedIn()
                if (isLoggedIn && settings.connectOnBoot) {
                    vpnConnectionManager.connectInBackground(quickConnectIntent(), Auto("legacy always-on"))
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
