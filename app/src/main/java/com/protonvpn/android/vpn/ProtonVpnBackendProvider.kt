/*
 * Copyright (c) 2019 Proton Technologies AG
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

import com.protonvpn.android.models.config.VpnProtocol
import com.protonvpn.android.models.profiles.Profile
import com.protonvpn.android.models.profiles.ServerDeliver
import com.protonvpn.android.models.vpn.Server
import com.protonvpn.android.utils.AndroidUtils.whenNotNullNorEmpty
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import me.proton.core.util.kotlin.mapAsync
import com.protonvpn.android.utils.ProtonLogger

class ProtonVpnBackendProvider(
    val strongSwan: VpnBackend,
    val openVpn: VpnBackend,
    val serverDeliver: ServerDeliver,
) : VpnBackendProvider {

    override suspend fun prepareConnection(
        protocol: VpnProtocol,
        profile: Profile,
        server: Server
    ): PrepareResult? {
        ProtonLogger.log("Preparing connection with protocol: " + protocol.name)
        return when (protocol) {
            VpnProtocol.IKEv2 -> strongSwan.prepareForConnection(profile, server, scan = false)
            VpnProtocol.OpenVPN -> openVpn.prepareForConnection(profile, server, scan = false)
            VpnProtocol.Smart ->
                strongSwan.prepareForConnection(profile, server, scan = true).takeIf { it.isNotEmpty() }
                    ?: openVpn.prepareForConnection(profile, server, scan = true)
        }.firstOrNull()
    }

    override suspend fun pingAll(preferenceList: List<PhysicalServer>): VpnBackendProvider.PingResult? {
        val responses = coroutineScope {
            preferenceList.mapAsync { server ->
                val profile = Profile.getTempProfile(server.server, serverDeliver)
                val strongSwanResponse = async {
                    strongSwan.prepareForConnection(profile, server.server, true, PING_ALL_MAX_PORTS)
                }
                val openVpnResponse = async {
                    openVpn.prepareForConnection(profile, server.server, true, PING_ALL_MAX_PORTS)
                }
                val responses = strongSwanResponse.await() + openVpnResponse.await()
                server to responses
            }.toMap()
        }

        preferenceList.forEach { server ->
            val serverResponses = responses[server]
            serverResponses.whenNotNullNorEmpty { responses ->
                return VpnBackendProvider.PingResult(responses.first().connectionParams.profile, server, responses)
            }
        }
        return null
    }

    companion object {
        private const val PING_ALL_MAX_PORTS = 3
    }
}
