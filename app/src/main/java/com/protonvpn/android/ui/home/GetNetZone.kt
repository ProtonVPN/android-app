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

import android.telephony.TelephonyManager
import com.protonvpn.android.di.ElapsedRealtimeClock
import com.protonvpn.android.di.WallClock
import com.protonvpn.android.utils.NetUtils
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GetNetZone @Inject constructor(
    private val prefs: ServerListUpdaterPrefs,
    private val telephonyManager: TelephonyManager?,
    @WallClock private val wallClock: () -> Long,
    @ElapsedRealtimeClock private val elapsedRealtimeMs: () -> Long,
) {
    var lastIpCheck = dateToRealtime(prefs.ipAddressCheckTimestamp)

    // Used in routes that provide server information including a score of how good a server is
    // for the particular user to connect to.
    // To provide relevant scores even when connected to VPN, we send a truncated version of
    // the user's public IP address. In keeping with our no-logs policy, this partial IP address
    // is not stored on the server and is only used to fulfill this one-off API request. If P
    // is not available/outdated we send network's MCC.
    operator fun invoke() = when {
        prefs.ipAddress.isNotBlank() && lastIpCheck >= elapsedRealtimeMs() - ServerListUpdater.IP_VALIDITY_MS ->
            NetUtils.stripIP(prefs.ipAddress)
        telephonyManager != null && telephonyManager.phoneType != TelephonyManager.PHONE_TYPE_CDMA ->
            telephonyManager.networkCountryIso
        prefs.ipAddress.isNotBlank() ->
            NetUtils.stripIP(prefs.ipAddress)
        else -> null
    }

    private fun dateToRealtime(date: Long) =
        elapsedRealtimeMs() - (wallClock() - date).coerceAtLeast(0)
}
