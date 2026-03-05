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
import com.protonvpn.android.notifications.NotificationChannels
import com.protonvpn.android.notifications.NotificationHelper
import com.protonvpn.android.redesign.vpn.AnyConnectIntent
import com.protonvpn.android.vpn.ReasonRestricted
import com.protonvpn.android.vpn.VpnUiDelegate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VpnBackgroundUiDelegate @Inject constructor (
    private val notificationHelper: NotificationHelper
) : VpnUiDelegate {

    override fun askForPermissions(intent: Intent, connectIntent: AnyConnectIntent, onPermissionGranted: () -> Unit) {
        // Can't ask for permissions when in background.
        showErrorNotification(
            R.string.insufficientPermissionsDetails,
            R.string.insufficientPermissionsTitle,
        )
    }

    override fun onServerRestricted(reason: ReasonRestricted): Boolean = false

    override fun onProtocolNotSupported() {
        showErrorNotification(R.string.profileProtocolNotAvailable)
    }

    fun showErrorNotification(@StringRes textRes: Int, @StringRes titleRes: Int? = null) {
        notificationHelper.showSimpleNotification(
            content = textRes,
            title = titleRes,
            icon = R.drawable.ic_vpn_status_disconnected,
            notificationChannelId = NotificationChannels.ID_CONNECTION_ERRORS,
        )
    }
}
