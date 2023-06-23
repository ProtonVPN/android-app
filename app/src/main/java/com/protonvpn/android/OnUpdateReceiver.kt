/*
 * Copyright (c) 2022 Proton AG
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

package com.protonvpn.android

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.protonvpn.android.appconfig.AppFeaturesPrefs
import com.protonvpn.android.logging.LogCategory
import com.protonvpn.android.logging.ProtonLogger
import com.protonvpn.android.models.vpn.ConnectionParams
import com.protonvpn.android.vpn.VpnConnectionManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class OnUpdateReceiver : BroadcastReceiver() {

    @Inject lateinit var vpnConnectionManager: VpnConnectionManager
    @Inject lateinit var appFeaturesPrefs: AppFeaturesPrefs

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            val connectIntent = ConnectionParams.readIntentFromStore()
            ProtonLogger.logCustom(
                LogCategory.APP_UPDATE,
                "ACTION_MY_PACKAGE_REPLACED has stored intent: ${connectIntent != null}"
            )
            if (connectIntent != null) {
                vpnConnectionManager.onRestoreProcess(connectIntent, "app update")
            }
        }
    }
}
