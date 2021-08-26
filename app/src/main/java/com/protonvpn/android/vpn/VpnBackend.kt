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

import androidx.annotation.CallSuper
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.protonvpn.android.appconfig.AppConfig
import com.protonvpn.android.models.config.UserData
import com.protonvpn.android.models.config.VpnProtocol
import com.protonvpn.android.models.profiles.Profile
import com.protonvpn.android.models.vpn.ConnectingDomain
import com.protonvpn.android.models.vpn.ConnectionParams
import com.protonvpn.android.models.vpn.Server
import com.protonvpn.android.utils.Constants
import com.protonvpn.android.utils.ProtonLogger
import com.protonvpn.android.utils.parallelSearch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.yield
import me.proton.core.network.domain.NetworkManager
import me.proton.core.network.domain.NetworkStatus
import me.proton.core.util.kotlin.DispatcherProvider
import me.proton.vpn.golib.localAgent.AgentConnection
import me.proton.vpn.golib.localAgent.Features
import me.proton.vpn.golib.localAgent.LocalAgent
import me.proton.vpn.golib.localAgent.NativeClient
import me.proton.vpn.golib.localAgent.StatusMessage
import me.proton.vpn.golib.vpnPing.VpnPing

private const val SCAN_TIMEOUT_MILLIS = 5000L

data class RetryInfo(val timeoutSeconds: Int, val retryInSeconds: Int)

data class PrepareResult(val backend: VpnBackend, val connectionParams: ConnectionParams) : java.io.Serializable

interface VpnBackendProvider {
    suspend fun prepareConnection(protocol: VpnProtocol, profile: Profile, server: Server): PrepareResult?

    // Returns first from [preferenceList] that responded in a given time frame or null
    // [fullScanServer] when set will have all ports scanned.
    suspend fun pingAll(preferenceList: List<PhysicalServer>, fullScanServer: PhysicalServer? = null): PingResult?
    data class PingResult(val profile: Profile, val physicalServer: PhysicalServer, val responses: List<PrepareResult>)
}

interface AgentConnectionInterface {
    val state: String
    fun setFeatures(features: Features)
    fun setConnectivity(connectivity: Boolean)
    fun close()
}

abstract class VpnBackend(
    val userData: UserData,
    val appConfig: AppConfig,
    val certificateRepository: CertificateRepository,
    private val networkManager: NetworkManager,
    val vpnProtocol: VpnProtocol,
    val mainScope: CoroutineScope,
    val dispatcherProvider: DispatcherProvider,
) : VpnStateSource {

    abstract suspend fun prepareForConnection(
        profile: Profile,
        server: Server,
        scan: Boolean,
        numberOfPorts: Int = Int.MAX_VALUE, // Max number of ports to be scanned
        waitForAll: Boolean = false // wait for all ports to respond if true, otherwise just wait for first successful
                                    // response
    ): List<PrepareResult>

    protected var lastConnectionParams: ConnectionParams? = null

    @CallSuper
    open suspend fun connect(connectionParams: ConnectionParams) {
        closeAgentConnection()
        lastConnectionParams = connectionParams
    }

    suspend fun disconnect() {
        if (vpnProtocolState != VpnState.Disabled)
            vpnProtocolState = VpnState.Disconnecting

        closeAgentConnection()
        closeVpnTunnel()
    }

    abstract suspend fun closeVpnTunnel()

    abstract suspend fun reconnect()

    open fun createAgentConnection(
        certInfo: CertificateRepository.CertificateResult.Success,
        hostname: String?,
        nativeClient: NativeClient
    ) = object : AgentConnectionInterface {
        val agent = AgentConnection(
            certInfo.certificate,
            certInfo.privateKeyPem,
            Constants.VPN_ROOT_CERTS,
            Constants.LOCAL_AGENT_ADDRESS,
            hostname,
            nativeClient,
            features,
            networkManager.isConnectedToNetwork()
        )

        override val state: String get() = agent.state

        override fun setFeatures(features: Features) {
            agent.setFeatures(features)
        }

        override fun setConnectivity(connectivity: Boolean) {
            agent.setConnectivity(connectivity)
        }

        override fun close() {
            agent.close()
        }
    }

    abstract val retryInfo: RetryInfo?

    // This is not a val because of how spyk() works in testing code: it creates a copy of the wrapped object and when
    // original object have "this" reference in a field, copy of that field in spyk() will point to the old object.
    private fun createNativeClient() = object : NativeClient {
        override fun log(msg: String) {
            ProtonLogger.log(msg)
        }

        override fun onError(code: Long, description: String) {
            ProtonLogger.log("Local agent error: $code $description")
            when (code) {
                agentConstants.errorCodeMaxSessionsBasic,
                agentConstants.errorCodeMaxSessionsFree,
                agentConstants.errorCodeMaxSessionsPlus,
                agentConstants.errorCodeMaxSessionsPro,
                agentConstants.errorCodeMaxSessionsUnknown,
                agentConstants.errorCodeMaxSessionsVisionary ->
                    //FIXME: set MAX_SESSIONS directly when error handling code is prepared for that
                    setAuthError("Max sessions reached")

                agentConstants.errorCodeBadCertSignature,
                agentConstants.errorCodeCertificateRevoked ->
                    revokeCertificateAndReconnect()

                agentConstants.errorCodeCertificateExpired ->
                    refreshCertOnLocalAgent(force = false)

                agentConstants.errorCodeKeyUsedMultipleTimes ->
                    setLocalAgentError("Key used multiple times")
                agentConstants.errorCodeUserTorrentNotAllowed ->
                    setLocalAgentError("Policy violation - torrent not allowed")
                agentConstants.errorCodeUserBadBehavior ->
                    setLocalAgentError("Bad behaviour")

                agentConstants.errorCodePolicyViolationLowPlan ->
                    setAuthError("Policy violation - too low plan")
                agentConstants.errorCodePolicyViolationDelinquent ->
                    setAuthError("Policy violation - pending invoice")
                agentConstants.errorCodeServerError ->
                    setAuthError("Server error")
                agentConstants.errorCodeRestrictedServer ->
                    // Server should unblock eventually, but we need to keep track and provide watchdog if necessary.
                    ProtonLogger.log("Local agent: Restricted server, waiting...")
            }
        }

        override fun onState(state: String) {
            ProtonLogger.log("Local agent state: $state")
            selfStateObservable.postValue(getGlobalVpnState(vpnProtocolState, state))
        }

        override fun onStatusUpdate(status: StatusMessage) {}
    }

    private fun setAuthError(description: String? = null) =
        selfStateObservable.postValue(VpnState.Error(ErrorType.AUTH_FAILED_INTERNAL, description))

    private fun setLocalAgentError(description: String? = null) =
        selfStateObservable.postValue(VpnState.Error(ErrorType.LOCAL_AGENT_ERROR, description))

    protected var vpnProtocolState: VpnState = VpnState.Disabled
        set(value) {
            field = value
            selfStateObservable.postValue(getGlobalVpnState(value, agent?.state))
        }

    override val selfStateObservable = MutableLiveData<VpnState>(VpnState.Disabled)
    private var agent: AgentConnectionInterface? = null
    private var agentConnectionJob: Job? = null
    private var reconnectionJob: Job? = null
    private val features: Features = Features()
    private val agentConstants = LocalAgent.constants()

    init {
        mainScope.launch {
            networkManager.observe().collect { status ->
                agent?.setConnectivity(status != NetworkStatus.Disconnected)
            }
        }

        initFeatures()
    }

    private val splitTcpValue get() = !appConfig.getFeatureFlags().vpnAccelerator || userData.isVpnAcceleratorEnabled

    private fun initFeatures() {
        observeFeature(userData.netShieldLiveData) {
            setInt(FEATURES_NETSHIELD, it.ordinal.toLong())
        }
        observeFeature(userData.vpnAcceleratorLiveData) {
            setBool(FEATURES_SPLIT_TCP, splitTcpValue)
        }
    }

    private fun <T> observeFeature(featureChange: LiveData<T>, update: Features.(T) -> Unit) {
        featureChange.observeForever {
            it?.let {
                features.update(it)
                agent?.setFeatures(features)
            }
        }
    }

    private fun prepareFeaturesForAgentConnection() {
        features.setBool(FEATURES_SPLIT_TCP, splitTcpValue)
        val bouncing = lastConnectionParams?.bouncing
        if (bouncing == null)
            features.remove(FEATURES_BOUNCING)
        else
            features.setString(FEATURES_BOUNCING, bouncing)
    }

    private fun getGlobalVpnState(vpnState: VpnState, localAgentState: String?): VpnState =
        if (vpnProtocol.localAgentEnabled() && userData.sessionId != null) {
            when (vpnState) {
                VpnState.Connected -> handleLocalAgentStates(localAgentState)
                VpnState.Disabled, VpnState.Disconnecting -> {
                    closeAgentConnection()
                    vpnState
                }
                else -> vpnState
            }
        } else vpnState

    private fun handleLocalAgentStates(localAgentState: String?): VpnState {
        connectToLocalAgent()
        return when (localAgentState) {
            agentConstants.stateConnected ->
                VpnState.Connected
            agentConstants.stateConnectionError,
            agentConstants.stateServerUnreachable ->
                VpnState.Error(ErrorType.UNREACHABLE)
            agentConstants.stateClientCertificateExpiredError -> {
                refreshCertOnLocalAgent(force = false)
                VpnState.Connecting
            }
            agentConstants.stateClientCertificateUnknownCA -> {
                refreshCertOnLocalAgent(force = true)
                VpnState.Connecting
            }
            agentConstants.stateServerCertificateError ->
                VpnState.Error(ErrorType.PEER_AUTH_FAILED)
            agentConstants.stateWaitingForNetwork ->
                VpnState.WaitingForNetwork
            agentConstants.stateHardJailed, // Error will be handled in NativeClient.onError method
            agentConstants.stateSoftJailed,
            agentConstants.stateConnecting ->
                VpnState.Connecting
            else ->
                VpnState.Connecting
        }
    }

    private fun refreshCertOnLocalAgent(force: Boolean) {
        selfStateObservable.postValue(VpnState.Connecting)
        closeAgentConnection()
        reconnectionJob = mainScope.launch {
            val result = if (force)
                certificateRepository.updateCertificate(userData.sessionId!!, false)
            else
                certificateRepository.getCertificate(userData.sessionId!!, false)
            when (result) {
                is CertificateRepository.CertificateResult.Success ->
                    connectToLocalAgent()
                is CertificateRepository.CertificateResult.Error -> {
                    // FIXME: eventually we'll need a more sophisticated logic that'd keep trying
                    ProtonLogger.log("Failed to refresh certificate")
                    setLocalAgentError("Failed to refresh certificate")
                }
            }
        }
    }

    private fun revokeCertificateAndReconnect() {
        selfStateObservable.postValue(VpnState.Connecting)
        closeAgentConnection()
        reconnectionJob = mainScope.launch {
            certificateRepository.generateNewKey(userData.sessionId!!)
            when (certificateRepository.updateCertificate(userData.sessionId!!, true)) {
                is CertificateRepository.CertificateResult.Error -> {
                    // FIXME: eventually we'll need a more sophisticated logic that'd keep trying
                    ProtonLogger.log("Failed to revoke and refresh certificate")
                    setLocalAgentError("Failed to refresh revoked certificate")
                }
                is CertificateRepository.CertificateResult.Success -> {
                    yield()
                    reconnect()
                }
            }
        }
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    fun connectToLocalAgent() {
        if (agent == null && agentConnectionJob == null) {
            val hostname = lastConnectionParams?.connectingDomain?.entryDomain
            agentConnectionJob = mainScope.launch {
                val certInfo = certificateRepository.getCertificate(userData.sessionId!!)
                if (certInfo is CertificateRepository.CertificateResult.Success) {
                    // Tunnel needs a moment to become functional
                    delay(500)

                    prepareFeaturesForAgentConnection()
                    agent = createAgentConnection(certInfo, hostname, createNativeClient())
                } else {
                    setLocalAgentError("Failed to get wireguard certificate")
                }
            }
        }
    }

    private fun closeAgentConnection() {
        reconnectionJob?.cancel()
        agentConnectionJob?.cancel()
        agentConnectionJob = null
        reconnectionJob = null
        agent?.close()
        agent = null
    }

    protected suspend fun waitForDisconnect() {
        withTimeoutOrNull(DISCONNECT_WAIT_TIMEOUT) {
            do {
                delay(200)
            } while (selfState != VpnState.Disabled)
        }
        if (selfState == VpnState.Disconnecting)
            setSelfState(VpnState.Disabled)
    }

    protected suspend fun scanUdpPorts(
        connectingDomain: ConnectingDomain,
        ports: List<Int>,
        numberOfPorts: Int,
        waitForAll: Boolean
    ): List<Int> = withContext(dispatcherProvider.Io) {
        if (connectingDomain.publicKeyX25519 == null)
            emptyList()
        else {
            val candidatePorts = if (numberOfPorts < ports.size)
                ports.shuffled().take(numberOfPorts)
            else
                ports.shuffled()

            candidatePorts.parallelSearch(waitForAll) {
                VpnPing.pingSync(connectingDomain.entryIp, it.toLong(),
                    connectingDomain.publicKeyX25519, SCAN_TIMEOUT_MILLIS)
            }
        }
    }

    companion object {
        private const val DISCONNECT_WAIT_TIMEOUT = 3000L
        private const val FEATURES_NETSHIELD = "netshield-level"
        private const val FEATURES_SPLIT_TCP = "split-tcp"
        private const val FEATURES_BOUNCING = "bouncing"
    }
}
