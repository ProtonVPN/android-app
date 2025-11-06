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

import com.protonvpn.android.R
import com.protonvpn.android.appconfig.FeatureFlags
import com.protonvpn.android.models.config.TransmissionProtocol
import com.protonvpn.android.models.config.VpnProtocol
import java.io.Serializable

@kotlinx.serialization.Serializable
data class ProtocolSelection private constructor(
    val vpn: VpnProtocol,
    val transmission: TransmissionProtocol?
) : Serializable {

    fun isSupported(featureFlags: FeatureFlags): Boolean {
        return when (vpn) {
            VpnProtocol.OpenVPN -> true
            VpnProtocol.ProTun -> true
            VpnProtocol.WireGuard -> when (transmission) {
                TransmissionProtocol.TCP, TransmissionProtocol.TLS -> featureFlags.wireguardTlsEnabled
                else -> true
            }
            VpnProtocol.Smart -> true
        }
    }

    val displayName: Int get() = when (vpn) {
        VpnProtocol.Smart -> R.string.settingsProtocolNameSmart
        VpnProtocol.ProTun -> when (transmission) {
            TransmissionProtocol.UDP -> R.string.settingsProtocolNameSmart
            TransmissionProtocol.TCP -> R.string.settingsProtocolNameWireguardTCP
            TransmissionProtocol.TLS -> R.string.settingsProtocolNameWireguardTLS
            null -> R.string.settingsProtocolNameWireguard
        }
        VpnProtocol.WireGuard -> when (transmission) {
            TransmissionProtocol.TCP -> R.string.settingsProtocolNameWireguardTCP
            TransmissionProtocol.TLS -> R.string.settingsProtocolNameWireguardTLS
            else -> R.string.settingsProtocolNameWireguard
        }
        VpnProtocol.OpenVPN -> when (transmission) {
            TransmissionProtocol.TCP -> R.string.settingsProtocolNameOpenVpnTcp
            else -> R.string.settingsProtocolNameOpenVpnUdp
        }
    }

    // Protocol name as used in API endpoints
    val apiName: String get() = "${vpn.apiName}${transmission?.name ?: ""}"

    companion object {
        @JvmStatic
        operator fun invoke(
            vpn: VpnProtocol,
            transmission: TransmissionProtocol? = null
        ) = ProtocolSelection(
            vpn,
            when (vpn) {
                VpnProtocol.Smart -> null
                VpnProtocol.ProTun -> transmission // null transmission for protun means all, not UDP
                else -> transmission ?: TransmissionProtocol.UDP
            }
        )

        val SMART = ProtocolSelection(VpnProtocol.Smart)
        val SMART_PROTUN = ProtocolSelection(VpnProtocol.ProTun, null)
        val STEALTH = ProtocolSelection(VpnProtocol.WireGuard, TransmissionProtocol.TLS)
        val REAL_PROTOCOLS = listOf(
            ProtocolSelection(VpnProtocol.WireGuard, TransmissionProtocol.UDP),
            ProtocolSelection(VpnProtocol.WireGuard, TransmissionProtocol.TCP),
            ProtocolSelection(VpnProtocol.OpenVPN, TransmissionProtocol.UDP),
            ProtocolSelection(VpnProtocol.OpenVPN, TransmissionProtocol.TCP),
            ProtocolSelection(VpnProtocol.WireGuard, TransmissionProtocol.TLS),
            ProtocolSelection(VpnProtocol.ProTun, TransmissionProtocol.UDP),
            ProtocolSelection(VpnProtocol.ProTun, TransmissionProtocol.TCP),
            ProtocolSelection(VpnProtocol.ProTun, TransmissionProtocol.TLS)
        )
        val PROTOCOLS_FOR: Map<VpnProtocol, List<ProtocolSelection>> =
            REAL_PROTOCOLS.groupBy { it.vpn }
    }
}

fun List<ProtocolSelection>.apiNames(): List<String> =
    map { it.apiName }.distinct()

fun ProtocolSelection.mapToProtun() = when(this) {
    ProtocolSelection.SMART -> ProtocolSelection.SMART_PROTUN
    else -> ProtocolSelection(VpnProtocol.ProTun, this.transmission)
}

fun ProtocolSelection.mapFromProtun() = when {
    this == ProtocolSelection.SMART_PROTUN -> ProtocolSelection.SMART
    else -> ProtocolSelection(VpnProtocol.WireGuard, transmission)
}

fun ProtocolSelection.effectiveProtocol(isProTunV1Enabled: Boolean) =
    if (!isProTunV1Enabled && vpn == VpnProtocol.ProTun) {
        mapFromProtun()
    } else {
        this
    }