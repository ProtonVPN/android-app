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

import com.protonvpn.android.ui.ForegroundActivityTracker
import com.protonvpn.android.vpn.VpnState
import com.protonvpn.android.vpn.VpnStateMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

@Suppress("UseDataClass")
@Singleton
class AppInUseMonitor @Inject constructor(
    mainScope: CoroutineScope,
    foregroundActivityTracker: ForegroundActivityTracker,
    vpnStateMonitor: VpnStateMonitor
) {
    /**
     * Indicates if the VPN app is in active use by the user.
     *
     * This information can be used to keep certain values up to date, e.g. by refreshing them from the backend.
     */
    val isInUseFlow = combine(
        vpnStateMonitor.status,
        foregroundActivityTracker.foregroundActivityFlow
    ) { status, fgActivity ->
        fgActivity != null || status.state != VpnState.Disabled
    }.stateIn(mainScope, SharingStarted.Eagerly, false)

    val isInUse: Boolean get() = isInUseFlow.value
}
