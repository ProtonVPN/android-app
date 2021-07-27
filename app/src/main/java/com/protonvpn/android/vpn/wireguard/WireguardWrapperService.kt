package com.protonvpn.android.vpn.wireguard
/*
 * Copyright (c) 2021 Proton Technologies AG
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

import com.protonvpn.android.components.NotificationHelper
import com.protonvpn.android.utils.Constants
import com.protonvpn.android.utils.ProtonLogger
import com.wireguard.android.backend.GoBackend
import dagger.android.AndroidInjection
import javax.inject.Inject

class WireguardWrapperService : GoBackend.VpnService() {

    @Inject lateinit var notificationHelper: NotificationHelper
    @Inject lateinit var wireguardBackend: WireguardBackend

    override fun onCreate() {
        super.onCreate()
        AndroidInjection.inject(this)
        startForeground(Constants.NOTIFICATION_ID, notificationHelper.buildNotification())
        wireguardBackend.serviceCreated(this)
    }

    override fun onDestroy() {
        wireguardBackend.serviceDestroyed()
        super.onDestroy()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        ProtonLogger.log("WirguardWrapperService: onTrimMemory level $level")
    }

    fun close() {
        stopForeground(false)
        stopSelf()
    }
}
