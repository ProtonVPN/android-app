/*
 * Copyright (c) 2020 Proton Technologies AG
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
import java.util.Locale

interface VpnStateSource {
    // value shouldn't be null
    val selfStateObservable: MutableLiveData<VpnState>
    val selfState get() = selfStateObservable.value!!

    fun setSelfState(value: VpnState) {
        selfStateObservable.value = value
    }
}

sealed class VpnState(val isEstablishingConnection: Boolean) {
    object Disabled : VpnState(false)

    object ScanningPorts : VpnState(true)
    object CheckingAvailability : VpnState(true)
    object WaitingForNetwork : VpnState(true)
    object Connecting : VpnState(true)
    object Reconnecting : VpnState(true)
    data class Error(val type: ErrorType) : VpnState(true) {
        override fun toString() = "$name($type)"
    }

    object Connected : VpnState(false)
    object Disconnecting : VpnState(false)

    val name = javaClass.simpleName.toUpperCase(Locale.ROOT)
    override fun toString() = name
}

enum class ErrorType {
    AUTH_FAILED_INTERNAL, AUTH_FAILED, PEER_AUTH_FAILED, LOOKUP_FAILED_INTERNAL, LOOKUP_FAILED,
    UNREACHABLE, UNREACHABLE_INTERNAL, MAX_SESSIONS, UNPAID, GENERIC_ERROR, MULTI_USER_PERMISSION
}
