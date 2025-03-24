/*
 * Copyright (c) 2021 Proton AG
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
package com.protonvpn.android.models.vpn.wireguard

import com.wireguard.android.backend.Statistics
import com.wireguard.android.backend.Tunnel
import com.wireguard.config.Config
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.withContext

class WireGuardTunnel internal constructor(
    private var name: String,
    config: Config?,
    state: Tunnel.State
) : Tunnel {

    override fun getName() = name

    private val internalStateFlow = MutableSharedFlow<Tunnel.State>(replay = 1, extraBufferCapacity = 5).apply {
        tryEmit(Tunnel.State.DOWN)
    }
    val stateFlow: Flow<Tunnel.State> = internalStateFlow

    var state = state
        private set

    override fun onStateChange(newState: Tunnel.State) {
        onStateChanged(newState)
    }

    fun onStateChanged(state: Tunnel.State): Tunnel.State {
        if (state != Tunnel.State.UP) onStatisticsChanged(null)
        this.state = state
        internalStateFlow.tryEmit(state)
        return state
    }

    suspend fun setStateAsync(state: Tunnel.State): Tunnel.State = withContext(Dispatchers.Main.immediate) {
        this@WireGuardTunnel.state
    }

    var config = config
        private set

    var statistics: Statistics? = null
        get() {
            // Wireguard fetches statistics from gobackend and uses their own tunnelManager.
            // So for now statistics are not hooked. Need to investigate more how we can do that and
            // if there is any benefit at all in hooking them. Since they contain just network statistics
            // which we already have.
        /*    if (field == null || field?.isStale != false) {
                   applicationScope.launch {
                       try {
                           manager.getTunnelStatistics(this@WireGuardTunnel)
                       } catch (e: Throwable) {
                           Log.e(TAG, Log.getStackTraceString(e))
                       }
                   }
            }
         */
            return field
        }
        private set

    /* suspend fun getStatisticsAsync(): Statistics = withContext(Dispatchers.Main.immediate) {
         statistics.let {
             if (it == null || it.isStale)
            //     manager.getTunnelStatistics(this@WireGuardTunnel)
             else
                 it
         }
     }*/

    fun onStatisticsChanged(statistics: Statistics?): Statistics? {
        this.statistics = statistics
        return statistics
    }
}
