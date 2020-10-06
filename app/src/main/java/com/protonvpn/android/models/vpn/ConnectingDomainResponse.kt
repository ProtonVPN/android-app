package com.protonvpn.android.models.vpn

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class ConnectingDomainResponse(
    @SerialName(value = "Server") val connectingDomain: ConnectingDomain
)
