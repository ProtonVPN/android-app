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
import com.protonvpn.android.models.profiles.Profile
import com.protonvpn.android.models.profiles.ServerDeliver
import com.protonvpn.android.ui.planupgrade.UpgradePlusCountriesDialogActivity
import com.protonvpn.android.ui.planupgrade.UpgradeSecureCoreDialogActivity
import com.protonvpn.android.vpn.PermissionContract
import com.protonvpn.android.vpn.ReasonRestricted
import com.protonvpn.android.vpn.VpnUiDelegate

abstract class VpnUiActivityDelegate(
    protected val activity: ComponentActivity
) : VpnUiDelegate {

    override fun askForPermissions(intent: Intent, profile: Profile, onPermissionGranted: () -> Unit) {
        val permissionCall = activity.activityResultRegistry.register(
            "VPNPermission", PermissionContract(intent)
        ) { permissionGranted ->
            if (permissionGranted) {
                onPermissionGranted()
            } else {
                onPermissionDenied(profile)
            }
        }
        permissionCall.launch(PermissionContract.VPN_PERMISSION_ACTIVITY)
    }

    abstract fun onPermissionDenied(profile: Profile)

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
    serverDeliver: ServerDeliver,
    retryConnection: ((Profile) -> Unit)? = null
) : VpnUiActivityDelegate(activity) {

    private val noVpnPermissionLauncher = activity.registerForActivityResult(
        NoVpnPermissionActivity.Companion.Contract()
    ) { retryProfile ->
        if (retryProfile != null) {
            retryProfile.wrapper.setDeliverer(serverDeliver)
            retryConnection?.invoke(retryProfile)
        }
    }

    override fun onPermissionDenied(profile: Profile) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
            noVpnPermissionLauncher.launch(profile)
    }

    override fun showPlusUpgradeDialog() {
        activity.startActivity(Intent(activity, UpgradePlusCountriesDialogActivity::class.java))
    }

    override fun showSecureCoreUpgradeDialog() {
        activity.startActivity(Intent(activity, UpgradeSecureCoreDialogActivity::class.java))
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
            .setMessage(R.string.serverNoWireguardSupport)
            .setPositiveButton(R.string.close, null)
            .show()
    }
}

