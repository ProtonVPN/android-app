package com.protonvpn.android.appconfig

import com.protonvpn.android.models.config.TransmissionProtocol
import com.protonvpn.android.models.config.VpnProtocol
import com.protonvpn.android.vpn.ProtocolSelection
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SmartProtocolConfig(
    @SerialName(value = "OpenVPN") val openVPNUdpEnabled: Boolean,
    @SerialName(value = "OpenVPNTCP") private val openVPNTcpEnabledInternal: Boolean? = null,
    @SerialName(value = "WireGuard") val wireguardEnabled: Boolean,
    @SerialName(value = "WireGuardTCP") val wireguardTcpEnabled: Boolean = true,
    @SerialName(value = "WireGuardTLS") val wireguardTlsEnabled: Boolean = true,
) {
    val openVPNTcpEnabled: Boolean get() = openVPNTcpEnabledInternal ?: openVPNUdpEnabled

    fun getSmartProtocols(): List<ProtocolSelection> =
        buildList {
            if (openVPNUdpEnabled)
                add(ProtocolSelection(VpnProtocol.OpenVPN, TransmissionProtocol.UDP))
            if (openVPNTcpEnabled)
                add(ProtocolSelection(VpnProtocol.OpenVPN, TransmissionProtocol.TCP))
            if (wireguardEnabled)
                add(ProtocolSelection(VpnProtocol.WireGuard, TransmissionProtocol.UDP))
            if (wireguardTcpEnabled)
                add(ProtocolSelection(VpnProtocol.WireGuard, TransmissionProtocol.TCP))
            if (wireguardTlsEnabled)
                add(ProtocolSelection(VpnProtocol.WireGuard, TransmissionProtocol.TLS))
        }
}
