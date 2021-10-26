/*
 * Copyright (c) 2021. Proton Technologies AG
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

package com.protonvpn.android.ui.vpn

import android.content.Context
import android.content.Intent
import androidx.activity.ComponentActivity
import com.protonvpn.android.vpn.PermissionContract
import com.protonvpn.android.vpn.VpnPermissionDelegate

class VpnPermissionActivityDelegate(
    private val activity: ComponentActivity,
    private val onPermissionDenied: () -> Unit
) : VpnPermissionDelegate {

    override fun askForPermissions(intent: Intent, onPermissionGranted: () -> Unit) {
        val permissionCall = activity.activityResultRegistry.register(
            "VPNPermission", PermissionContract(intent)
        ) { permissionGranted ->
            if (permissionGranted) {
                onPermissionGranted()
            } else {
                onPermissionDenied()
            }
        }
        permissionCall.launch(PermissionContract.VPN_PERMISSION_ACTIVITY)
    }

    override fun getContext(): Context = activity
}