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

import android.content.Context
import androidx.lifecycle.MutableLiveData
import com.protonvpn.android.R
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
    data class Error(val type: ErrorType, val description: String? = null) : VpnState(true) {
        override fun toString() = "$name($type) + $description"
    }

    object Connected : VpnState(false)
    object Disconnecting : VpnState(false)

    val name = javaClass.simpleName.toUpperCase(Locale.ROOT)
    override fun toString() = name
}

enum class ErrorType {
    AUTH_FAILED_INTERNAL,
    AUTH_FAILED,
    PEER_AUTH_FAILED,
    LOOKUP_FAILED_INTERNAL,
    UNREACHABLE,
    UNREACHABLE_INTERNAL,
    MAX_SESSIONS,
    GENERIC_ERROR,
    MULTI_USER_PERMISSION,
    LOCAL_AGENT_ERROR,
    SERVER_ERROR,
    POLICY_VIOLATION_DELINQUENT,
    POLICY_VIOLATION_LOW_PLAN,
    POLICY_VIOLATION_BAD_BEHAVIOUR,
    TORRENT_NOT_ALLOWED,
    KEY_USED_MULTIPLE_TIMES;

    fun mapToErrorMessage(context: Context, additionalDetails: String? = null): String {
        val stringResId = when (this) {
            PEER_AUTH_FAILED -> R.string.error_peer_auth_failed
            AUTH_FAILED -> R.string.error_auth_failed
            UNREACHABLE -> R.string.error_server_unreachable
            POLICY_VIOLATION_DELINQUENT -> R.string.errorUserDelinquent
            MULTI_USER_PERMISSION -> R.string.errorTunMultiUserPermission
            TORRENT_NOT_ALLOWED -> R.string.errorTorrentNotAllowed
            else ->
                if (additionalDetails.isNullOrEmpty())
                    R.string.error_generic
                else
                    R.string.error_generic_with_reason
        }
        return context.getString(stringResId, additionalDetails)
    }
}
