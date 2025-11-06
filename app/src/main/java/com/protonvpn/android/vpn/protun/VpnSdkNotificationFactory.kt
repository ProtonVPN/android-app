/*
 * Copyright (c) 2025 Proton AG
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

package com.protonvpn.android.vpn.protun

import android.content.Context
import com.protonvpn.android.notifications.NotificationHelper
import com.protonvpn.android.utils.Constants
import dagger.Lazy
import dagger.Reusable
import me.proton.vpn.sdk.api.ForegroundServiceNotificationFactory
import me.proton.vpn.sdk.api.VpnConnectionState
import javax.inject.Inject

@Reusable
class VpnSdkNotificationFactory @Inject constructor(
    private val notificationHelper: Lazy<NotificationHelper>
) : ForegroundServiceNotificationFactory {

    override val notificationId: Int
        get() = Constants.NOTIFICATION_ID

    override fun buildNotification(context: Context, state: VpnConnectionState) =
        notificationHelper.get().buildNotification()
}