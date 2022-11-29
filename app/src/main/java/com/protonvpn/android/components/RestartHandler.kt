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

package com.protonvpn.android.components

import com.protonvpn.android.appconfig.AppFeaturesPrefs
import com.protonvpn.android.di.WallClock
import com.protonvpn.android.models.vpn.ConnectionParams
import com.protonvpn.android.vpn.VpnConnectionManager
import dagger.Reusable
import javax.inject.Inject

private const val MIN_RESTART_DELAY_FOR_RECONNECT_MS = 15_000L

@Reusable
class RestartHandler @Inject constructor(
    private val vpnConnectionManager: VpnConnectionManager,
    private val appFeaturesPrefs: AppFeaturesPrefs,
    @WallClock private val wallClock: () -> Long
) {

    fun onAppStarted() {
        val previousAppStartTimestamp = appFeaturesPrefs.lastAppStartTimestamp
        appFeaturesPrefs.lastAppStartTimestamp = wallClock()

        // Restore connection. This should be done automatically by Android by restarting VPN service that was last
        // connected (because they are "sticky") but it doesn't always work.
        // Don't connect if the last start was just a moment ago to avoid crash loops.
        val storedParams = ConnectionParams.readFromStore()
        if (storedParams != null && wallClock() - previousAppStartTimestamp >= MIN_RESTART_DELAY_FOR_RECONNECT_MS) {
            vpnConnectionManager.onRestoreProcess(storedParams.profile, "app start")
        }
    }
}
