/*
 * Copyright (c) 2022 Proton AG
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

import android.os.Parcelable

import com.protonvpn.android.R
import com.protonvpn.android.models.config.TransmissionProtocol
import com.protonvpn.android.models.config.VpnProtocol
import kotlinx.parcelize.Parcelize

sealed class ProtocolSelection(val protocol: VpnProtocol) : Parcelable {
    @Parcelize
    object Smart : ProtocolSelection(VpnProtocol.Smart) {
        override val displayName: Int = R.string.settingsProtocolNameSmart
    }

    @Parcelize
    object WireGuard : ProtocolSelection(VpnProtocol.WireGuard) {
        override val displayName: Int = R.string.settingsProtocolNameWireguard
    }

    @Parcelize
    object IKEv2 : ProtocolSelection(VpnProtocol.IKEv2) {
        override val displayName: Int = R.string.settingsProtocolNameIkeV2
    }

    @Parcelize
    data class OpenVPN(val transmissionProtocol: TransmissionProtocol) : ProtocolSelection(VpnProtocol.OpenVPN) {
        override val displayName: Int = when (transmissionProtocol) {
            TransmissionProtocol.TCP -> R.string.settingsProtocolNameOpenVpnTcp
            TransmissionProtocol.UDP -> R.string.settingsProtocolNameOpenVpnUdp
        }
    }

    abstract val displayName: Int

    val transmission get() = (this as? OpenVPN)?.transmissionProtocol

    companion object {
        @JvmStatic
        fun from(protocol: VpnProtocol, transmissionProtocol: TransmissionProtocol? = null): ProtocolSelection =
            when (protocol) {
                VpnProtocol.Smart -> Smart
                VpnProtocol.WireGuard -> WireGuard
                VpnProtocol.IKEv2 -> IKEv2
                VpnProtocol.OpenVPN -> OpenVPN(requireNotNull(transmissionProtocol))
            }
    }
}
