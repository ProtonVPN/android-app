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

package com.protonvpn.android.tv.usecases

import android.content.Context
import android.content.DialogInterface
import com.protonvpn.android.R
import com.protonvpn.android.components.BaseTvActivity
import com.protonvpn.android.logging.ProtonLogger
import com.protonvpn.android.logging.UiConnect
import com.protonvpn.android.logging.UiDisconnect
import com.protonvpn.android.redesign.vpn.ConnectIntent
import com.protonvpn.android.tv.showTvDialog
import com.protonvpn.android.vpn.ConnectTrigger
import com.protonvpn.android.vpn.DisconnectTrigger
import com.protonvpn.android.vpn.VpnConnectionManager
import dagger.Reusable
import javax.inject.Inject

@Reusable
class TvUiConnectDisconnectHelper @Inject constructor(
    private val vpnConnectionManager: VpnConnectionManager
) {
    fun connect(activity: BaseTvActivity, connectIntent: ConnectIntent?, trigger: ConnectTrigger) {
        if (connectIntent != null) {
            ProtonLogger.log(UiConnect, trigger.description)
            vpnConnectionManager.connect(activity.getVpnUiDelegate(), connectIntent, trigger)
        } else {
            showMaintenanceDialog(activity)
        }
    }

    fun disconnect(trigger: DisconnectTrigger) {
        ProtonLogger.log(UiDisconnect, trigger.description)
        vpnConnectionManager.disconnect(trigger)
    }

    fun showMaintenanceDialog(context: Context) {
        showTvDialog(context, DialogInterface.BUTTON_NEUTRAL) {
            setTitle(R.string.tv_country_maintenance_dialog_title)
            setMessage(R.string.tv_country_maintenance_dialog_description)
            setNeutralButton(R.string.ok, null)
        }
    }
}
