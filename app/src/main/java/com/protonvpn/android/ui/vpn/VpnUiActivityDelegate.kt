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

package com.protonvpn.android.ui.vpn

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import androidx.activity.ComponentActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.protonvpn.android.R
import com.protonvpn.android.logging.ConnError
import com.protonvpn.android.logging.ProtonLogger
import com.protonvpn.android.redesign.vpn.AnyConnectIntent
import com.protonvpn.android.ui.planupgrade.CarouselUpgradeDialogActivity
import com.protonvpn.android.ui.planupgrade.UpgradePlusCountriesHighlightsFragment
import com.protonvpn.android.ui.planupgrade.UpgradeSecureCoreHighlightsFragment
import com.protonvpn.android.utils.haveVpnSettings
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
                ProtonLogger.log(ConnError, "VPN permission denied. have_vpn_settings=${activity.haveVpnSettings()}")
                onPermissionDenied(connectIntent)
            }
        }
        try {
            permissionCall.launch(PermissionContract.VPN_PERMISSION_ACTIVITY)
        } catch (e: ActivityNotFoundException) {
            onNoVpnSupport()
        }
    }

    abstract fun onPermissionDenied(connectIntent: AnyConnectIntent)
    abstract fun onNoVpnSupport()

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
        CarouselUpgradeDialogActivity.launch<UpgradePlusCountriesHighlightsFragment>(activity)
    }

    override fun showSecureCoreUpgradeDialog() {
        CarouselUpgradeDialogActivity.launch<UpgradeSecureCoreHighlightsFragment>(activity)
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

    override fun onNoVpnSupport() {
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.dialogVpnNotSupportedOnThisDeviceTitle)
            .setMessage(R.string.dialogVpnNotSupportedOnThisDeviceDescription)
            .setPositiveButton(R.string.close, null)
            .show()
    }
}
