/*
 * Copyright (c) 2024. Proton AG
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

package com.protonvpn.android.models.vpn

import com.protonvpn.android.redesign.vpn.AnyConnectIntent
import com.protonvpn.android.settings.data.SplitTunnelingMode
import com.protonvpn.android.settings.data.SplitTunnelingSettings

interface SplitTunnelAppsConfigurator {
    fun includeApplications(packageNames: List<String>)
    fun excludeApplications(packageNames: List<String>)
}

fun applyAppsSplitTunneling(
    configurator: SplitTunnelAppsConfigurator,
    connectIntent: AnyConnectIntent,
    myPackageName: String,
    splitTunneling: SplitTunnelingSettings,
) {
    with(splitTunneling) {
        when {
            connectIntent is AnyConnectIntent.GuestHole ->
                configurator.includeApplications(listOf(myPackageName))

            isEnabled && mode == SplitTunnelingMode.INCLUDE_ONLY && includedApps.isNotEmpty() -> {
                configurator.includeApplications(listOf(myPackageName))
                configurator.includeApplications(includedApps)
            }

            isEnabled && mode == SplitTunnelingMode.EXCLUDE_ONLY && excludedApps.isNotEmpty() ->
                configurator.excludeApplications(excludedApps - myPackageName)
        }
    }
}
