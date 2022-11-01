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

import com.protonvpn.android.logging.ConnConnectScanFailed
import com.protonvpn.android.logging.ProtonLogger
import com.protonvpn.android.models.config.TransmissionProtocol
import com.protonvpn.android.utils.parallelSearch
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import me.proton.core.util.kotlin.filterNullValues
import javax.inject.Inject

private const val SCAN_TIMEOUT_MILLIS = 5000

class ServerAvailabilityCheck @Inject constructor(val serverPing: ServerPing) {

    data class Destination(val ip: String, val ports: List<Int>)

    /**
     * Pings ports on multiple destinations in parallel.
     * @param waitForAll if true will wait for all destinations to either respond or timeout. if false it'll return
     *    after single response on each destination.
     * @return list of ports that responded.
     */
    suspend fun pingInParallel(
        destinations: Map<TransmissionProtocol, Destination>,
        waitForAll: Boolean,
        publicKeyX25519: String?
    ): Map<TransmissionProtocol, Destination> = coroutineScope {
        // If IP and ports are the same for TCP and TLS let's only ping them once.
        val copyTlsResult = destinations[TransmissionProtocol.TCP] == destinations[TransmissionProtocol.TLS]
        destinations.mapValues { (transmission, dest) ->
            async {
                if (transmission == TransmissionProtocol.UDP && publicKeyX25519 == null) {
                    ProtonLogger.log(ConnConnectScanFailed, "no public key")
                    null
                } else if (copyTlsResult && transmission == TransmissionProtocol.TLS) {
                    null
                } else {
                    dest.ports.parallelSearch(waitForAll, priorityWaitMs = PING_PRIORITY_WAIT_DELAY) { port ->
                        val data = getPingData(transmission, publicKeyX25519)
                        serverPing.ping(dest.ip, port, data, tcp = transmission != TransmissionProtocol.UDP,
                            timeout = SCAN_TIMEOUT_MILLIS)
                    }.takeIf { it.isNotEmpty() }?.let { livePorts ->
                        Destination(dest.ip, livePorts)
                    }
                }
            }
        }.mapValues {
            it.value.await()
        }.let { result ->
            if (copyTlsResult)
                result.toMutableMap().apply { this[TransmissionProtocol.TLS] = this[TransmissionProtocol.TCP] }
            else
                result
        }.filterNullValues()
    }

    private fun getPingData(transmission: TransmissionProtocol, publicKeyX25519: String?) =
        if (transmission != TransmissionProtocol.UDP)
            ByteArray(0)
        else
            serverPing.buildUdpPingData(publicKeyX25519)

    companion object {
        // During this time pings will prefer ports in order in which they were defined
        const val PING_PRIORITY_WAIT_DELAY = 1000L
    }
}
