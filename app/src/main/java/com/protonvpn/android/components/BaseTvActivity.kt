/*
 * Copyright (c) 2020 Proton Technologies AG
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

import android.annotation.TargetApi
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.fragment.app.FragmentActivity
import com.afollestad.materialdialogs.DialogAction
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.Theme
import com.protonvpn.android.R
import com.protonvpn.android.ui.vpn.VpnPermissionActivityDelegate
import com.protonvpn.android.utils.Constants
import com.protonvpn.android.utils.HtmlTools
import com.protonvpn.android.vpn.VpnPermissionDelegate

abstract class BaseTvActivity : FragmentActivity(), VpnPermissionDelegate {

    @Suppress("LeakingThis")
    private val vpnPermissionDelegate =
        VpnPermissionActivityDelegate(this) { onVpnPermissionDenied() }

    override fun askForPermissions(intent: Intent, onPermissionGranted: () -> Unit) {
        vpnPermissionDelegate.askForPermissions(intent, onPermissionGranted)
    }

    override fun getContext(): Context = this

    private fun onVpnPermissionDenied() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            showNoVpnPermissionDialog()
        }
    }

    @TargetApi(Build.VERSION_CODES.N)
    private fun showNoVpnPermissionDialog() {
        val content = HtmlTools.fromHtml(
            getString(
                R.string.error_prepare_vpn_description, Constants.URL_SUPPORT_PERMISSIONS
            )
        )
        MaterialDialog.Builder(this).theme(Theme.DARK).title(R.string.error_prepare_vpn_title)
            .content(content).positiveText(R.string.error_prepare_vpn_settings)
            .onPositive { _: MaterialDialog?, _: DialogAction? ->
                startActivity(Intent(Settings.ACTION_VPN_SETTINGS))
            }.show()
    }
}
