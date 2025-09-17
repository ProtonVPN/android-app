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

package com.protonvpn.android.tv.vpn

import android.annotation.TargetApi
import android.content.DialogInterface
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.ComponentActivity
import com.protonvpn.android.R
import com.protonvpn.android.models.features.PaidFeature
import com.protonvpn.android.redesign.vpn.AnyConnectIntent
import com.protonvpn.android.tv.showTvDialog
import com.protonvpn.android.tv.ui.TvKeyConstants
import com.protonvpn.android.tv.upsell.TvUpsellActivity
import com.protonvpn.android.ui.vpn.VpnUiActivityDelegate
import com.protonvpn.android.utils.Constants
import com.protonvpn.android.utils.DebugUtils
import com.protonvpn.android.utils.HtmlTools
import com.protonvpn.android.utils.haveVpnSettings

class VpnUiActivityDelegateTv(
    activity: ComponentActivity
) : VpnUiActivityDelegate(activity) {

    override fun onPermissionDenied(connectIntent: AnyConnectIntent) {
        if (activity.haveVpnSettings()) {
            showNoVpnPermissionDialog()
        }
    }

    override fun showPlusUpgradeDialog() {
        val intent = Intent(activity, TvUpsellActivity::class.java).apply {
            putExtra(TvKeyConstants.PAID_FEATURE, PaidFeature.AllCountries)
        }

        activity.startActivity(intent)
    }

    override fun showSecureCoreUpgradeDialog() {
        DebugUtils.debugAssert("Secure Core not supported on TV") { false }
        showPlusUpgradeDialog()
    }

    override fun showMaintenanceDialog() {
        showTvDialog(activity, focusedButton = DialogInterface.BUTTON_NEUTRAL) {
            setTitle(R.string.restrictedMaintenanceTitle)
            setMessage(R.string.restrictedMaintenanceDescription)
            setNeutralButton(R.string.got_it, null)
        }
    }

    override fun onProtocolNotSupported() {
        showTvDialog(activity, focusedButton = DialogInterface.BUTTON_NEUTRAL) {
            setMessage(R.string.profileProtocolNotAvailable)
            setNeutralButton(R.string.close, null)
        }
    }

    @TargetApi(Build.VERSION_CODES.N)
    private fun showNoVpnPermissionDialog() {
        val content = HtmlTools.fromHtml(
            activity.getString(
                R.string.error_prepare_vpn_description, Constants.URL_SUPPORT_PERMISSIONS
            )
        )
        showTvDialog(activity, focusedButton = DialogInterface.BUTTON_POSITIVE) {
            setTitle(R.string.error_prepare_vpn_title)
            setMessage(content)
            setPositiveButton(R.string.error_prepare_vpn_settings) { _, _ ->
                activity.startActivity(Intent(Settings.ACTION_VPN_SETTINGS))
            }
        }
    }

    override fun onNoVpnSupport() {
        showTvDialog(activity, focusedButton = DialogInterface.BUTTON_NEUTRAL) {
            setTitle(R.string.dialogVpnNotSupportedOnThisDeviceTitle)
            setMessage(R.string.dialogVpnNotSupportedOnThisDeviceDescription)
            setNeutralButton(R.string.close, null)
        }
    }
}
