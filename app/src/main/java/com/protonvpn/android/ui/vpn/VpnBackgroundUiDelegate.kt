/*
 * Copyright (c) 2022. Proton AG
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
import androidx.annotation.StringRes
import com.protonvpn.android.R
import com.protonvpn.android.models.profiles.Profile
import com.protonvpn.android.notifications.NotificationHelper
import com.protonvpn.android.vpn.ReasonRestricted
import com.protonvpn.android.vpn.VpnUiDelegate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VpnBackgroundUiDelegate @Inject constructor (
    private val notificationHelper: NotificationHelper
) : VpnUiDelegate {

    override fun askForPermissions(intent: Intent, profile: Profile, onPermissionGranted: () -> Unit) {
        // Can't ask for permissions when in background.
        notificationHelper.showInformationNotification(
            R.string.insufficientPermissionsDetails,
            R.string.insufficientPermissionsTitle,
            icon = R.drawable.ic_vpn_status_disconnected
        )
    }

    override fun onServerRestricted(reason: ReasonRestricted): Boolean = false

    override fun onProtocolNotSupported() {
        notificationHelper.showInformationNotification(
            R.string.serverNoWireguardSupport,
            icon = R.drawable.ic_vpn_status_disconnected
        )
    }

    fun showInfoNotification(@StringRes textRes: Int) {
        notificationHelper.showInformationNotification(textRes)
    }
}
