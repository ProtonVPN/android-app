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

import android.content.Intent
import android.os.Build
import androidx.activity.ComponentActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.protonvpn.android.R
import com.protonvpn.android.redesign.vpn.AnyConnectIntent
import com.protonvpn.android.ui.planupgrade.UpgradeDialogActivity
import com.protonvpn.android.ui.planupgrade.UpgradePlusCountriesHighlightsFragment
import com.protonvpn.android.ui.planupgrade.UpgradeSecureCoreHighlightsFragment
import com.protonvpn.android.vpn.PermissionContract
import com.protonvpn.android.vpn.ReasonRestricted
import com.protonvpn.android.vpn.VpnUiDelegate

abstract class VpnUiActivityDelegate(
    protected val activity: ComponentActivity
) : VpnUiDelegate {

    override fun askForPermissions(intent: Intent, connectIntent: AnyConnectIntent, onPermissionGranted: () -> Unit) {
        val permissionCall = activity.activityResultRegistry.register(
            "VPNPermission", PermissionContract(intent)
        ) { permissionGranted ->
            if (permissionGranted) {
                onPermissionGranted()
            } else {
                onPermissionDenied(connectIntent)
            }
        }
        permissionCall.launch(PermissionContract.VPN_PERMISSION_ACTIVITY)
    }

    abstract fun onPermissionDenied(connectIntent: AnyConnectIntent)

    abstract fun showPlusUpgradeDialog()
    abstract fun showMaintenanceDialog()
    abstract fun showSecureCoreUpgradeDialog()

    override fun onServerRestricted(reason: ReasonRestricted): Boolean {
        when (reason) {
            ReasonRestricted.SecureCoreUpgradeNeeded -> showSecureCoreUpgradeDialog()
            ReasonRestricted.PlusUpgradeNeeded -> showPlusUpgradeDialog()
            ReasonRestricted.Maintenance -> showMaintenanceDialog()
        }
        return true
    }
}

class VpnUiActivityDelegateMobile(
    activity: ComponentActivity,
    retryConnection: ((AnyConnectIntent) -> Unit)? = null
) : VpnUiActivityDelegate(activity) {

    private val noVpnPermissionLauncher = activity.registerForActivityResult(
        NoVpnPermissionActivity.Companion.Contract()
    ) { retryConnectIntent ->
        if (retryConnectIntent != null) {
            retryConnection?.invoke(retryConnectIntent)
        }
    }

    override fun onPermissionDenied(connectIntent: AnyConnectIntent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
            noVpnPermissionLauncher.launch(connectIntent)
    }

    override fun showPlusUpgradeDialog() {
        UpgradeDialogActivity.launch<UpgradePlusCountriesHighlightsFragment>(activity)
    }

    override fun showSecureCoreUpgradeDialog() {
        UpgradeDialogActivity.launch<UpgradeSecureCoreHighlightsFragment>(activity)
    }

    override fun showMaintenanceDialog() {
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.restrictedMaintenanceTitle)
            .setMessage(R.string.restrictedMaintenanceDescription)
            .setNegativeButton(R.string.got_it, null)
            .show()
    }

    override fun onProtocolNotSupported() {
        MaterialAlertDialogBuilder(activity)
            .setMessage(R.string.profileProtocolNotAvailable)
            .setPositiveButton(R.string.close, null)
            .show()
    }
}
