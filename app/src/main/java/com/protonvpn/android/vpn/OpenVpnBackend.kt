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

import android.content.Intent
import android.os.Build
import com.protonvpn.android.ProtonApplication
import com.protonvpn.android.models.config.TransmissionProtocol
import com.protonvpn.android.models.config.UserData
import com.protonvpn.android.models.profiles.Profile
import com.protonvpn.android.models.vpn.ConnectingDomain
import com.protonvpn.android.models.vpn.ConnectionParamsOpenVpn
import com.protonvpn.android.models.vpn.Server
import com.protonvpn.android.appconfig.AppConfig
import com.protonvpn.android.utils.Constants
import com.protonvpn.android.utils.DebugUtils
import com.protonvpn.android.utils.Log
import com.protonvpn.android.utils.NetUtils
import com.protonvpn.android.utils.ProtonLogger
import com.protonvpn.android.utils.implies
import com.protonvpn.android.utils.randomNullable
import de.blinkt.openvpn.core.ConnectionStatus
import de.blinkt.openvpn.core.OpenVPNService.PAUSE_VPN
import de.blinkt.openvpn.core.VpnStatus
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.apache.commons.codec.binary.Hex
import org.apache.commons.codec.digest.HmacAlgorithms
import org.apache.commons.codec.digest.HmacUtils
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.util.Random

class OpenVpnBackend(
    val random: Random,
    val userData: UserData,
    val appConfig: AppConfig,
    val unixTime: () -> Long
) : VpnBackend("OpenVpn"), VpnStatus.StateListener {

    init {
        VpnStatus.addStateListener(this)
        VpnStatus.addLogListener {
            ProtonLogger.log(it.getString(ProtonApplication.getAppContext()))
        }
    }

    data class ProtocolInfo(val transmissionProtocol: TransmissionProtocol, val port: Int)

    override suspend fun prepareForConnection(profile: Profile, server: Server, scan: Boolean): PrepareResult? {
        val connectingDomain = server.getRandomConnectingDomain()
        val openVpnPorts = appConfig.getOpenVPNPorts()
        val protocolInfo = if (!scan) {
            val transmissionProtocol = profile.getTransmissionProtocol(userData)
            val port = (if (transmissionProtocol == TransmissionProtocol.UDP)
                openVpnPorts.getUdpPorts() else openVpnPorts.getTcpPorts()).random()
            ProtocolInfo(transmissionProtocol, port)
        } else {
            scanPorts(connectingDomain)
        }
        return protocolInfo?.let {
            PrepareResult(this, ConnectionParamsOpenVpn(
                    profile, server, connectingDomain, it.transmissionProtocol, it.port))
        }
    }

    private suspend fun scanPorts(connectingDomain: ConnectingDomain): ProtocolInfo? {
        val openVpnPorts = appConfig.getOpenVPNPorts()
        val udpPingData = getPingData(tcp = false)
        var transmissionProtocol = TransmissionProtocol.UDP
        var availablePort = scanInParallel(
                openVpnPorts.getUdpPorts().shuffled(), connectingDomain.entryIp, udpPingData, withTcp = false)

        if (availablePort == null) {
            val tcpPingData = getPingData(tcp = true)
            transmissionProtocol = TransmissionProtocol.TCP
            availablePort = scanInParallel(
                    openVpnPorts.getTcpPorts().shuffled(), connectingDomain.entryIp, tcpPingData, withTcp = true)
        }
        return availablePort?.let { ProtocolInfo(transmissionProtocol, it) }
    }

    private suspend fun scanInParallel(
        ports: List<Int>,
        ip: String,
        data: ByteArray,
        withTcp: Boolean
    ): Int? = coroutineScope {
        val available = ports.map { port ->
            async {
                port.takeIf {
                    NetUtils.ping(ip, port, data, withTcp, timeout = 3000)
                }
            }
        }.mapNotNull {
            it.await()
        }
        available.randomNullable()
    }

    private fun getPingData(tcp: Boolean): ByteArray {
        // P_CONTROL_HARD_RESET_CLIENT_V2 TLS message.
        // see: https://build.openvpn.net/doxygen/network_protocol.html

        val sessionId = ByteArray(8)
        random.nextBytes(sessionId)
        val timestamp = (unixTime() / 1000).toInt()

        val packet = byteArrayBuilder {
            writeInt(1)
            writeInt(timestamp)
            write(7 shl 3)
            write(sessionId)
            write(0)
            writeInt(0)
        }

        val tlsAuthKeyHex = Constants.TLS_AUTH_KEY_HEX.replace("\n", "")
        val tlsAuthKey = Hex.decodeHex(tlsAuthKeyHex.toCharArray()).drop(192).take(64).toByteArray()
        val hmac = HmacUtils.getInitializedMac(HmacAlgorithms.HMAC_SHA_512, tlsAuthKey).doFinal(packet)

        val authenticatedPacket = byteArrayBuilder {
            write(7 shl 3)
            write(sessionId)
            write(hmac)
            writeInt(1)
            writeInt(timestamp)
            write(0)
            writeInt(0)
        }

        return if (tcp) byteArrayBuilder {
            writeShort(authenticatedPacket.size)
            write(authenticatedPacket)
        } else
            authenticatedPacket
    }

    private fun byteArrayBuilder(block: DataOutputStream.() -> Unit): ByteArray {
        val byteStream = ByteArrayOutputStream()
        DataOutputStream(byteStream).use(block)
        return byteStream.toByteArray()
    }

    override suspend fun connect() {
        startOpenVPN(null)
    }

    override suspend fun disconnect() {
        if (selfState != VpnState.Disabled) {
            selfStateObservable.value = VpnState.Disconnecting
        }
        // In some scenarios OpenVPN might start a connection in a moment even if it's in the
        // disconnected state - request pause regardless of the state
        startOpenVPN(PAUSE_VPN)
        waitForDisconnect()
    }

    override suspend fun reconnect() {
        disconnect()
        startOpenVPN(null)
    }

    // No retry info available for open vpn
    override val retryInfo: RetryInfo? get() = null

    private fun startOpenVPN(action: String?) {
        val ovpnService =
                Intent(ProtonApplication.getAppContext(), OpenVPNWrapperService::class.java)
        if (action != null)
            ovpnService.action = action
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ProtonApplication.getAppContext().startForegroundService(ovpnService)
        } else {
            ProtonApplication.getAppContext().startService(ovpnService)
        }
    }

    override fun updateState(
        openVpnState: String?,
        logmessage: String?,
        localizedResId: Int,
        level: ConnectionStatus?,
        Intent: Intent?
    ) {
        if (level == ConnectionStatus.LEVEL_CONNECTING_NO_SERVER_REPLY_YET &&
                (selfState as? VpnState.Error)?.type == ErrorType.PEER_AUTH_FAILED) {
            // On tls-error OpenVPN will send a single RECONNECTING state update with tls-error in
            // logmessage followed by LEVEL_CONNECTING_NO_SERVER_REPLY_YET updates without info
            // about tls-error. Let's stay in PEER_AUTH_FAILED for the rest of this connection
            // attempt.
            return
        }

        val translatedState = if (openVpnState == "RECONNECTING" && logmessage?.startsWith("tls-error") == true) {
            VpnState.Error(ErrorType.PEER_AUTH_FAILED)
        } else if (openVpnState == "RECONNECTING") {
            VpnState.Reconnecting
        } else when (level) {
            ConnectionStatus.LEVEL_CONNECTED ->
                VpnState.Connected
            ConnectionStatus.LEVEL_CONNECTING_SERVER_REPLIED, ConnectionStatus.LEVEL_CONNECTING_NO_SERVER_REPLY_YET,
            ConnectionStatus.LEVEL_START, ConnectionStatus.LEVEL_WAITING_FOR_USER_INPUT ->
                VpnState.Connecting
            ConnectionStatus.LEVEL_NONETWORK ->
                VpnState.WaitingForNetwork
            ConnectionStatus.LEVEL_NOTCONNECTED, ConnectionStatus.LEVEL_VPNPAUSED ->
                VpnState.Disabled
            ConnectionStatus.LEVEL_AUTH_FAILED ->
                VpnState.Error(ErrorType.AUTH_FAILED_INTERNAL)
            ConnectionStatus.UNKNOWN_LEVEL ->
                VpnState.Error(ErrorType.GENERIC_ERROR)
            ConnectionStatus.LEVEL_MULTI_USER_PERMISSION ->
                VpnState.Error(ErrorType.MULTI_USER_PERMISSION)
            null -> VpnState.Disabled
        }
        DebugUtils.debugAssert {
            (translatedState in arrayOf(VpnState.Connecting, VpnState.Connected)).implies(active)
        }
        selfStateObservable.postValue(translatedState)
    }

    override fun setConnectedVPN(uuid: String) {
        Log.e("set connected vpn: $uuid")
    }
}
