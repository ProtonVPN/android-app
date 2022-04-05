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
package com.protonvpn.android.utils

import com.protonvpn.android.logging.ConnConnectScanFailed
import com.protonvpn.android.logging.ProtonLogger
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import me.proton.core.network.domain.ApiResult
import java.io.IOException
import java.lang.Long.min
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketAddress
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.random.Random

object NetUtils {

    // Strips (replace with 0) the last segment of IPv4 and network interface part of IPv6 (long format) addresses
    fun stripIP(ip: String): String {
        return """(.*)\.[0-9]+""".toRegex().matchEntire(ip)?.let {
            "${it.groups[1]!!.value}.0"
        }
            ?: """([0-9a-fA-F]+:[0-9a-fA-F]+:[0-9a-fA-F]+:[0-9a-fA-F]+):.*""".toRegex().matchEntire(ip)?.let {
                "${it.groups[1]!!.value}:0:0:0:0"
            } ?: ip
    }

    suspend fun ping(
        ip: String,
        port: Int,
        pingData: ByteArray,
        tcp: Boolean,
        timeout: Int = 5000
    ): Boolean = withContext(Dispatchers.IO) {
        suspendCancellableCoroutine<Boolean> { continuation ->
            val result = try {
                val address = InetSocketAddress(InetAddress.getByName(ip), port)
                val result = if (tcp)
                    pingTcp(pingData, address, continuation, timeout)
                else
                    pingUdp(pingData, address, continuation, timeout)
                result
            } catch (e: IOException) {
                val protocol = (if (tcp) "TCP" else "UDP")
                ProtonLogger.log(
                    ConnConnectScanFailed,
                    "destination: $ip:$port ($protocol); error: $e"
                )
                false
            }
            continuation.resume(result)
        }
    }

    private fun pingTcp(
        pingData: ByteArray,
        socketAddress: SocketAddress,
        continuation: CancellableContinuation<*>,
        timeout: Int
    ): Boolean {
        Socket().use { socket ->
            continuation.invokeOnCancellation {
                socket.close()
            }
            socket.soTimeout = timeout
            socket.connect(socketAddress, timeout)
            socket.getOutputStream().apply {
                write(pingData)
                flush()
            }
            return socket.getInputStream().read() != -1
        }
    }

    private fun pingUdp(
        pingData: ByteArray,
        socketAddress: SocketAddress,
        continuation: CancellableContinuation<*>,
        timeout: Int
    ): Boolean {
        DatagramSocket().use { socket ->
            continuation.invokeOnCancellation {
                socket.close()
            }
            val packet = DatagramPacket(pingData, pingData.size, socketAddress)
            socket.soTimeout = timeout
            socket.send(packet)
            val returnPacket = DatagramPacket(ByteArray(1), 1)
            socket.receive(returnPacket)
            return returnPacket.length > 0
        }
    }
}

fun jitterMs(
    baseMs: Long,
    diff: Float = .2f,
    maxMs: Long = TimeUnit.HOURS.toMillis(1),
    random: Random = Random
): Long {
    val rangeMs = min(maxMs, (diff * baseMs).toLong())
    return baseMs + random.nextLong(rangeMs)
}

fun ApiResult.Error.displayText(): String? =
    (this as? ApiResult.Error.Http)?.proton?.error ?: cause?.localizedMessage
