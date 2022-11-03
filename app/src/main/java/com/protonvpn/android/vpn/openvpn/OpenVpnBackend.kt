/*
 * Copyright (c) 2021 Proton Technologies AG
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
package com.protonvpn.android.vpn.openvpn

import android.content.Context
import android.content.Intent
import android.os.Build
import com.protonvpn.android.appconfig.AppConfig
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.logging.LogCategory
import com.protonvpn.android.logging.LogLevel
import com.protonvpn.android.logging.ProtonLogger
import com.protonvpn.android.models.config.TransmissionProtocol
import com.protonvpn.android.models.config.UserData
import com.protonvpn.android.models.config.VpnProtocol
import com.protonvpn.android.models.profiles.Profile
import com.protonvpn.android.models.vpn.ConnectionParams
import com.protonvpn.android.models.vpn.ConnectionParamsOpenVpn
import com.protonvpn.android.models.vpn.Server
import com.protonvpn.android.ui.home.GetNetZone
import com.protonvpn.android.utils.DebugUtils
import com.protonvpn.android.utils.Log
import com.protonvpn.android.vpn.CertificateRepository
import com.protonvpn.android.vpn.ErrorType
import com.protonvpn.android.vpn.LocalAgentUnreachableTracker
import com.protonvpn.android.vpn.PrepareResult
import com.protonvpn.android.vpn.RetryInfo
import com.protonvpn.android.vpn.ServerPing
import com.protonvpn.android.vpn.VpnBackend
import com.protonvpn.android.vpn.VpnState
import dagger.hilt.android.qualifiers.ApplicationContext
import de.blinkt.openvpn.core.ConnectionStatus
import de.blinkt.openvpn.core.LogItem
import de.blinkt.openvpn.core.OpenVPNService.PAUSE_VPN
import de.blinkt.openvpn.core.VpnStatus
import kotlinx.coroutines.CoroutineScope
import me.proton.core.network.data.di.SharedOkHttpClient
import me.proton.core.network.domain.NetworkManager
import me.proton.core.util.kotlin.DispatcherProvider
import okhttp3.OkHttpClient
import java.util.Random
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OpenVpnBackend @Inject constructor(
    @ApplicationContext private val appContext: Context,
    val random: Random,
    networkManager: NetworkManager,
    userData: UserData,
    appConfig: AppConfig,
    certificateRepository: CertificateRepository,
    mainScope: CoroutineScope,
    dispatcherProvider: DispatcherProvider,
    serverPing: ServerPing,
    localAgentUnreachableTracker: LocalAgentUnreachableTracker,
    currentUser: CurrentUser,
    getNetZone: GetNetZone,
    @SharedOkHttpClient okHttp: OkHttpClient
) : VpnBackend(
    userData,
    appConfig,
    certificateRepository,
    networkManager,
    VpnProtocol.OpenVPN,
    mainScope,
    dispatcherProvider,
    serverPing,
    localAgentUnreachableTracker,
    currentUser,
    getNetZone,
    okHttp
), VpnStatus.StateListener {

    init {
        VpnStatus.addStateListener(this)
        VpnStatus.addLogListener(this::vpnLog)
    }

    override suspend fun prepareForConnection(
        profile: Profile,
        server: Server,
        transmissionProtocols: Set<TransmissionProtocol>,
        scan: Boolean,
        numberOfPorts: Int,
        waitForAll: Boolean
    ): List<PrepareResult> {
        val connectingDomain = server.getRandomConnectingDomain()
        val openVpnPorts = appConfig.getOpenVPNPorts()
        val protocolInfo = if (!scan) {
            DebugUtils.debugAssert { transmissionProtocols.size == 1 }
            val transmission = transmissionProtocols.first()
            val ports = if (transmission == TransmissionProtocol.TCP) openVpnPorts.tcpPorts else openVpnPorts.udpPorts
            listOf(ProtocolInfo(transmission, ports.random()))
        } else {
            scanPorts(connectingDomain, numberOfPorts, transmissionProtocols, waitForAll, openVpnPorts, PRIMARY_PORT)
        }
        return protocolInfo.map {
            PrepareResult(this, ConnectionParamsOpenVpn(
                profile, server, connectingDomain, it.transmissionProtocol, it.port))
        }
    }

    override suspend fun connect(connectionParams: ConnectionParams) {
        super.connect(connectionParams)
        startOpenVPN(null)
    }

    override suspend fun closeVpnTunnel(withStateChange: Boolean) {
        // In some scenarios OpenVPN might start a connection in a moment even if it's in the
        // disconnected state - request pause regardless of the state
        startOpenVPN(PAUSE_VPN)
        waitForDisconnect()
    }

    // No retry info available for open vpn
    override val retryInfo: RetryInfo? get() = null

    private fun startOpenVPN(action: String?) {
        val ovpnService =
                Intent(appContext, OpenVPNWrapperService::class.java)
        if (action != null)
            ovpnService.action = action
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            appContext.startForegroundService(ovpnService)
        } else {
            appContext.startService(ovpnService)
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
                (vpnProtocolState as? VpnState.Error)?.type == ErrorType.PEER_AUTH_FAILED) {
            // On tls-error OpenVPN will send a single RECONNECTING state update with tls-error in
            // logmessage followed by LEVEL_CONNECTING_NO_SERVER_REPLY_YET updates without info
            // about tls-error. Let's stay in PEER_AUTH_FAILED for the rest of this connection
            // attempt.
            return
        }

        val translatedState = when {
            openVpnState == "CONNECTRETRY" && level == ConnectionStatus.LEVEL_CONNECTING_NO_SERVER_REPLY_YET ->
                VpnState.Error(ErrorType.UNREACHABLE_INTERNAL)
            openVpnState == "RECONNECTING" -> if (logmessage?.startsWith("tls-error") == true)
                VpnState.Error(ErrorType.PEER_AUTH_FAILED) else VpnState.Reconnecting
            else -> when (level) {
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
        }
        vpnProtocolState = translatedState
    }

    override fun setConnectedVPN(uuid: String) {
        Log.e("set connected vpn: $uuid")
    }

    private fun vpnLog(item: LogItem) {
        val logLevel = when(item.logLevel) {
            VpnStatus.LogLevel.VERBOSE -> LogLevel.TRACE
            VpnStatus.LogLevel.DEBUG -> LogLevel.DEBUG
            VpnStatus.LogLevel.INFO -> LogLevel.INFO
            VpnStatus.LogLevel.WARNING -> LogLevel.WARN
            VpnStatus.LogLevel.ERROR -> LogLevel.ERROR
            null -> LogLevel.INFO
        }
        ProtonLogger.logCustom(logLevel, LogCategory.PROTOCOL, item.getString(appContext))
    }

    companion object {
        private const val PRIMARY_PORT = 443
    }
}
