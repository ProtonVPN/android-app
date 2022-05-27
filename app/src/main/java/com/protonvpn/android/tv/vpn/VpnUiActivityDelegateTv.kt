/*
 * Copyright (c) 2022 Proton Technologies AG
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
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.ComponentActivity
import com.afollestad.materialdialogs.DialogAction
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.Theme
import com.protonvpn.android.R
import com.protonvpn.android.models.profiles.Profile
import com.protonvpn.android.tv.TvUpgradeActivity
import com.protonvpn.android.ui.vpn.VpnUiActivityDelegate
import com.protonvpn.android.utils.Constants
import com.protonvpn.android.utils.HtmlTools

class VpnUiActivityDelegateTv(
    activity: ComponentActivity
) : VpnUiActivityDelegate(activity) {

    override fun onPermissionDenied(profile: Profile) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            showNoVpnPermissionDialog()
        }
    }

    override fun showPlusUpgradeDialog() {
        activity.startActivity(Intent(activity, TvUpgradeActivity::class.java))
    }

    override fun showMaintenanceDialog() {
        MaterialDialog.Builder(activity).theme(Theme.DARK)
            .title(R.string.restrictedMaintenanceTitle)
            .content(R.string.restrictedMaintenanceDescription)
            .negativeFocus(true)
            .negativeText(R.string.got_it)
            .show()
    }

    override fun onProtocolNotSupported() {
        MaterialDialog.Builder(activity).theme(Theme.DARK)
            .content(R.string.serverNoWireguardSupport)
            .positiveText(R.string.close)
            .show()
    }

    @TargetApi(Build.VERSION_CODES.N)
    private fun showNoVpnPermissionDialog() {
        val content = HtmlTools.fromHtml(
            activity.getString(
                R.string.error_prepare_vpn_description, Constants.URL_SUPPORT_PERMISSIONS
            )
        )
        MaterialDialog.Builder(activity).theme(Theme.DARK).title(R.string.error_prepare_vpn_title)
            .content(content).positiveText(R.string.error_prepare_vpn_settings)
            .onPositive { _: MaterialDialog?, _: DialogAction? ->
                activity.startActivity(Intent(Settings.ACTION_VPN_SETTINGS))
            }.show()
    }
}
