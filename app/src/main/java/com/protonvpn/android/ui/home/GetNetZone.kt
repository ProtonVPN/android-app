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

package com.protonvpn.android.ui.home

import com.protonvpn.android.utils.NetUtils
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GetNetZone @Inject constructor(
    private val prefs: ServerListUpdaterPrefs,
) {
    // Used in routes that provide server information including a score of how good a server is
    // for the particular user to connect to.
    // To provide relevant scores even when connected to VPN, we send a truncated version of
    // the user's public IP address. In keeping with our no-logs policy, this partial IP address
    // is not stored on the server and is only used to fulfill this one-off API request.
    operator fun invoke(): String? =
        if (prefs.ipAddress.isNotBlank())
            NetUtils.stripIP(prefs.ipAddress)
        else
            null

    fun updateIp(ip: String) {
        prefs.ipAddress = ip
    }

    fun updateCountry(country: String?) {
        prefs.lastKnownCountry = country
    }
}
