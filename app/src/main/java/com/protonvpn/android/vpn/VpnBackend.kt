/*
 * Copyright (c) 2019 Proton Technologies AG
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
package com.protonvpn.android.vpn

import androidx.lifecycle.MutableLiveData
import com.protonvpn.android.models.config.UserData
import com.protonvpn.android.models.profiles.Profile
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

data class RetryInfo(val timeoutSeconds: Int, val retryInSeconds: Int)

interface VpnBackendProvider {
    fun getFor(userData: UserData, profile: Profile? = null): VpnBackend
}

abstract class VpnBackend(val name: String) {
    abstract suspend fun connect()
    abstract suspend fun disconnect()
    abstract suspend fun reconnect()
    abstract val retryInfo: RetryInfo?

    fun setState(newState: VpnStateMonitor.State) {
        stateObservable.value = newState
    }

    protected suspend fun waitForDisconnect() {
        withTimeoutOrNull(DISCONNECT_WAIT_TIMEOUT) {
            do {
                delay(200)
            } while (state != VpnStateMonitor.State.DISABLED)
        }
        if (state == VpnStateMonitor.State.DISCONNECTING)
            stateObservable.value = VpnStateMonitor.State.DISABLED
    }

    var active = false
    val stateObservable = MutableLiveData<VpnStateMonitor.State>().apply {
        value = VpnStateMonitor.State.DISABLED
    }

    val state get() = stateObservable.value!!
    var error: ConnectionError = ConnectionError(VpnStateMonitor.ErrorState.NO_ERROR)

    companion object {
        private const val DISCONNECT_WAIT_TIMEOUT = 3000L
    }
}
