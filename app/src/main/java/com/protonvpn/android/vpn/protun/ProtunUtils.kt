/*
 * Copyright (c) 2026 Proton AG
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

package com.protonvpn.android.vpn.protun

import com.protonvpn.android.utils.DebugUtils
import com.protonvpn.android.vpn.ErrorType
import com.protonvpn.android.vpn.VpnState
import me.proton.vpn.core.api.PeerConnectionWaitReason
import me.proton.vpn.core.api.VpnConnectionState
import me.proton.vpn.core.api.VpnDisconnectError

fun VpnConnectionState.toLegacyState(inV1mode: Boolean = false) = when (this) {
    VpnConnectionState.Loading -> VpnState.Disabled

    is VpnConnectionState.Disconnected ->
        error?.toLegacyErrorType() ?: VpnState.Disabled

    is VpnConnectionState.Connecting -> {
        if (PeerConnectionWaitReason.WaitingForNetwork in waitReasons)
            VpnState.WaitingForNetwork
        else
            VpnState.Connecting
    }

    is VpnConnectionState.ConnectingToLocalAgent -> {
        if (inV1mode) {
            DebugUtils.debugAssert("Unexpected state in NoLocalAgent mode") { false }
        }
        VpnState.Connecting
    }

    is VpnConnectionState.Connected -> VpnState.Connected
}

fun VpnDisconnectError.toLegacyErrorType(): VpnState = when (this) {
    is VpnDisconnectError.ServiceError ->
        VpnState.Error(ErrorType.GENERIC_ERROR, message, isFinal = true)

    is VpnDisconnectError.TunInterfaceError ->
        VpnState.Error(ErrorType.GENERIC_ERROR, message, isFinal = true)

    VpnDisconnectError.VpnPermissionMissing ->
        VpnState.Error(
            ErrorType.GENERIC_ERROR,
            "VPN permission missing",
            isFinal = true
        )

    VpnDisconnectError.InteractAcrossUsers ->
        VpnState.Error(ErrorType.MULTI_USER_PERMISSION, null, isFinal = true)

    // Local agent - won't happen on v1
    is VpnDisconnectError.AppError.UnrecoverableJail ->
        VpnState.Error(ErrorType.GENERIC_ERROR, reason.name, isFinal = true)

    is VpnDisconnectError.AppError.Other ->
        VpnState.Error(ErrorType.GENERIC_ERROR, e.message, isFinal = true)

    VpnDisconnectError.ServiceRevoked ->
        VpnState.Disabled
}