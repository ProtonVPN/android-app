/*
 * Copyright (c) 2023. Proton AG
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

package com.protonvpn.android.telemetry

import com.protonvpn.android.ui.home.ServerListUpdaterPrefs
import com.protonvpn.android.vpn.VpnStateMonitor
import dagger.Reusable
import javax.inject.Inject

@Reusable
class CommonDimensions @Inject constructor(
    private val vpnStateMonitor: VpnStateMonitor,
    private val prefs: ServerListUpdaterPrefs,
) {

    enum class Key {
        ISP,
        USER_COUNTRY,
        VPN_STATUS;

        val reportedName = name.lowercase()
    }

    fun add(dimensions: MutableMap<String, String>, vararg keys: Key) {
        fun dimension(key: Key, value: () -> String) {
            if (keys.contains(key)) dimensions[key.reportedName] = value()
        }

        dimension(Key.ISP) { prefs.lastKnownIsp ?: NO_VALUE }
        dimension(Key.USER_COUNTRY) { prefs.lastKnownCountry?.uppercase() ?: NO_VALUE }
        dimension(Key.VPN_STATUS) { if (vpnStateMonitor.isConnected) "on" else "off" }
    }

    companion object {
        const val NO_VALUE = "n/a"
    }
}
