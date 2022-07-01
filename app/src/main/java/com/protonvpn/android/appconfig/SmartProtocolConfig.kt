package com.protonvpn.android.appconfig

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SmartProtocolConfig(
    @SerialName(value = "IKEv2") val ikeV2Enabled: Boolean,
    @SerialName(value = "OpenVPN") val openVPNEnabled: Boolean,
    @SerialName(value = "WireGuard") val wireguardEnabled: Boolean,
    @SerialName(value = "WireGuardTCP") val wireguardTcpEnabled: Boolean = false,
    @SerialName(value = "WireGuardTLS") val wireguardTlsEnabled: Boolean = false,
)
