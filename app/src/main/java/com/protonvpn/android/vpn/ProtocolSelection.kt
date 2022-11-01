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

import com.google.gson.annotations.SerializedName
import com.protonvpn.android.R
import com.protonvpn.android.appconfig.FeatureFlags
import com.protonvpn.android.models.config.TransmissionProtocol
import com.protonvpn.android.models.config.VpnProtocol
import java.io.Serializable

data class ProtocolSelection private constructor(
    val vpn: VpnProtocol,
    val transmission: TransmissionProtocol?
) : Serializable {

    @SerializedName("protocol") private val migrateVpn: VpnProtocol? = null

    fun migratingFromIKEv2() = vpn == null

    // Migrate from custom test builds and after removing IKEv2.
    fun migrate(): ProtocolSelection =
        when {
            vpn == null && migrateVpn != null -> invoke(migrateVpn, transmission)
            migratingFromIKEv2() -> invoke(VpnProtocol.Smart) // Unsupported protocol, fallback to Smart
            else -> this
        }

    fun localAgentEnabled() = vpn.localAgentEnabled()

    fun isSupported(featureFlags: FeatureFlags): Boolean {
        return when (vpn) {
            VpnProtocol.OpenVPN -> true
            VpnProtocol.WireGuard -> when (transmission) {
                TransmissionProtocol.TCP, TransmissionProtocol.TLS -> featureFlags.wireguardTlsEnabled
                else -> true
            }
            VpnProtocol.Smart -> true
        }
    }

    val displayName: Int get() = when (vpn) {
        VpnProtocol.Smart -> R.string.settingsProtocolNameSmart
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
    val apiName: String get() = "${vpn.name}${transmission?.name ?: ""}"

    companion object {
        @JvmStatic
        operator fun invoke(
            vpn: VpnProtocol,
            transmission: TransmissionProtocol? = null
        ) = ProtocolSelection(
            vpn,
            if (vpn == VpnProtocol.Smart)
                null
            else
                transmission ?: TransmissionProtocol.UDP
        )

        val REAL_PROTOCOLS = listOf(
            ProtocolSelection(VpnProtocol.WireGuard, TransmissionProtocol.UDP),
            ProtocolSelection(VpnProtocol.WireGuard, TransmissionProtocol.TCP),
            ProtocolSelection(VpnProtocol.OpenVPN, TransmissionProtocol.UDP),
            ProtocolSelection(VpnProtocol.OpenVPN, TransmissionProtocol.TCP),
            ProtocolSelection(VpnProtocol.WireGuard, TransmissionProtocol.TLS),
        )
    }
}
