package com.protonvpn.test.shared

import com.protonvpn.android.models.vpn.GatewayGroup
import com.protonvpn.android.servers.Server

object TestGatewayGroup {

    val empty: GatewayGroup = create(name = "Test Empty Gateway Group")

    fun create(
        name: String = "Test Gateway Group",
        vararg server: Server,
    ): GatewayGroup = GatewayGroup(
        name = name,
        serverList = server.toList(),
    )

}
