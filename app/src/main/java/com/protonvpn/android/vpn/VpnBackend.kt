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
import com.protonvpn.android.logging.ProtonLogger
import com.protonvpn.android.models.config.UserData
import com.protonvpn.android.models.config.VpnProtocol
import com.protonvpn.android.models.profiles.Profile
import com.protonvpn.android.models.vpn.ConnectingDomain
import com.protonvpn.android.models.vpn.ConnectionParams
import com.protonvpn.android.models.vpn.Server
import com.protonvpn.android.utils.Constants
import com.protonvpn.android.utils.parallelSearch
import com.protonvpn.android.utils.takeRandomStable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.yield
import me.proton.core.network.domain.NetworkManager
import me.proton.core.network.domain.NetworkStatus
import me.proton.core.util.kotlin.DispatcherProvider
import com.proton.gopenpgp.localAgent.AgentConnection
import com.proton.gopenpgp.localAgent.Features
import com.proton.gopenpgp.localAgent.LocalAgent
import com.proton.gopenpgp.localAgent.NativeClient
import com.proton.gopenpgp.localAgent.StatusMessage
import com.proton.gopenpgp.vpnPing.VpnPing
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.logging.ConnConnectScan
import com.protonvpn.android.logging.ConnConnectScanFailed
import com.protonvpn.android.logging.ConnError
import com.protonvpn.android.logging.LocalAgentError
import com.protonvpn.android.logging.LocalAgentStateChanged
import com.protonvpn.android.logging.LocalAgentStatus
import com.protonvpn.android.logging.LogCategory
import com.protonvpn.android.logging.UserCertRefresh
import com.protonvpn.android.logging.UserCertRevoked
import com.protonvpn.android.utils.LiveEvent
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

private const val SCAN_TIMEOUT_MILLIS = 5000L
private const val MAX_LOCAL_AGENT_CONNECTION_FAILURES = 3

data class RetryInfo(val timeoutSeconds: Int, val retryInSeconds: Int)

data class PrepareResult(val backend: VpnBackend, val connectionParams: ConnectionParams) : java.io.Serializable

interface VpnBackendProvider {
    suspend fun prepareConnection(
        protocol: VpnProtocol,
        profile: Profile,
        server: Server,
        alwaysScan: Boolean = true
    ): PrepareResult?

    // Returns first from [preferenceList] that responded in a given time frame or null
    // [fullScanServer] when set will have all ports scanned.
    suspend fun pingAll(preferenceList: List<PhysicalServer>, fullScanServer: PhysicalServer? = null): PingResult?
    data class PingResult(val profile: Profile, val physicalServer: PhysicalServer, val responses: List<PrepareResult>)
}

interface AgentConnectionInterface {
    val state: String
    val status: StatusMessage?
    val unableToConnect: Boolean
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
    val currentUser: CurrentUser
) : VpnStateSource {

    inner class VpnAgentClient : NativeClient {
        private val agentConstants = LocalAgent.constants()

        @Volatile private var unreachableCount = 0
        val failedTooManyTimes: Boolean get() = unreachableCount >= MAX_LOCAL_AGENT_CONNECTION_FAILURES

        override fun log(msg: String) {
            ProtonLogger.logCustom(LogCategory.LOCAL_AGENT, msg)
        }

        override fun onError(code: Long, description: String) {
            ProtonLogger.log(LocalAgentError, "code: $code, $description")
            when (code) {
                agentConstants.errorCodeMaxSessionsBasic,
                agentConstants.errorCodeMaxSessionsFree,
                agentConstants.errorCodeMaxSessionsPlus,
                agentConstants.errorCodeMaxSessionsPro,
                agentConstants.errorCodeMaxSessionsUnknown,
                agentConstants.errorCodeMaxSessionsVisionary ->
                    setError(ErrorType.MAX_SESSIONS)

                agentConstants.errorCodeBadCertSignature,
                agentConstants.errorCodeCertificateRevoked ->
                    revokeCertificateAndReconnect("local agent error: $description ($code)")

                agentConstants.errorCodeCertificateExpired ->
                    refreshCertOnLocalAgent(force = false, reason = "local agent: certificate expired")

                agentConstants.errorCodeKeyUsedMultipleTimes ->
                    setError(ErrorType.KEY_USED_MULTIPLE_TIMES)
                agentConstants.errorCodeUserTorrentNotAllowed ->
                    setError(ErrorType.TORRENT_NOT_ALLOWED)
                agentConstants.errorCodeUserBadBehavior ->
                    setError(ErrorType.POLICY_VIOLATION_BAD_BEHAVIOUR)
                agentConstants.errorCodePolicyViolationLowPlan ->
                    setError(ErrorType.POLICY_VIOLATION_LOW_PLAN)
                agentConstants.errorCodePolicyViolationDelinquent ->
                    setError(ErrorType.POLICY_VIOLATION_DELINQUENT)
                agentConstants.errorCodeServerError ->
                    setError(ErrorType.SERVER_ERROR)
                agentConstants.errorCodeRestrictedServer ->
                    // Server should unblock eventually, but we need to keep track and provide watchdog if necessary.
                    ProtonLogger.logCustom(LogCategory.LOCAL_AGENT, "Restricted server, waiting...")
                else -> {
                    if (agent?.status?.reason?.final == true)
                        setError(ErrorType.LOCAL_AGENT_ERROR, description = description)
                }
            }
        }

        override fun onState(state: String) {
            ProtonLogger.log(LocalAgentStateChanged, state)
            when (state) {
                agentConstants.stateServerUnreachable,
                agentConstants.stateConnectionError ->
                    unreachableCount++
                agentConstants.stateConnected,
                agentConstants.stateDisconnected ->
                    unreachableCount = 0
            }
            processCombinedState(vpnProtocolState, state)
        }

        override fun onStatusUpdate(status: StatusMessage) {
            ProtonLogger.log(LocalAgentStatus, status.toString())
        }
    }

    protected var lastConnectionParams: ConnectionParams? = null

    abstract suspend fun prepareForConnection(
        profile: Profile,
        server: Server,
        scan: Boolean,
        numberOfPorts: Int = Int.MAX_VALUE, // Max number of ports to be scanned
        waitForAll: Boolean = false // wait for all ports to respond if true, otherwise just wait for first successful
                                    // response
    ): List<PrepareResult>

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

    protected abstract suspend fun closeVpnTunnel(withStateChange: Boolean = true)

    open suspend fun reconnect() {
        lastConnectionParams?.let { params ->
            disconnect()
            connect(params)
        }
    }

    open fun createAgentConnection(
        certInfo: CertificateRepository.CertificateResult.Success,
        hostname: String?,
        nativeClient: VpnAgentClient
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
        override val status: StatusMessage? get() = agent.status
        override val unableToConnect: Boolean get() = nativeClient.failedTooManyTimes

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
    private fun createNativeClient() = VpnAgentClient()

    private fun setError(error: ErrorType, disconnectVPN: Boolean = true, description: String? = null) {
        description?.let {
            ProtonLogger.log(ConnError, it)
        }
        mainScope.launch {
            if (disconnectVPN) {
                closeAgentConnection()
                closeVpnTunnel(withStateChange = false)
            }

            selfStateObservable.setValue(VpnState.Error(error, description))
        }
    }

    protected var vpnProtocolState: VpnState = VpnState.Disabled
        set(value) {
            field = value
            processCombinedState(value, agent?.state)
        }

    override val selfStateObservable = MutableLiveData<VpnState>(VpnState.Disabled)
    private var agent: AgentConnectionInterface? = null
    private var agentConnectionJob: Job? = null
    private var reconnectionJob: Job? = null
    private val features: Features = Features()
    private val agentConstants = LocalAgent.constants()

    init {
        networkManager.observe().onEach { status ->
            agent?.setConnectivity(status != NetworkStatus.Disconnected)
        }.launchIn(mainScope)

        certificateRepository.currentCertUpdateFlow.onEach {
            if (agent != null) {
                closeAgentConnection()
                connectToLocalAgent()
            }
        }.launchIn(mainScope)

        initFeatures()
    }

    private val splitTcpValue get() = userData.isVpnAcceleratorEnabled(appConfig.getFeatureFlags())
    private val safeModeValue get() = userData.isSafeModeEnabled(appConfig.getFeatureFlags())

    private fun initFeatures() {
        observeFeature(userData.netShieldSettingUpdateEvent) {
            setInt(FEATURES_NETSHIELD, userData.getNetShieldProtocol(currentUser.vpnUserCached()).ordinal.toLong())
        }
        observeFeature(userData.randomizedNatLiveData) { randomizedNat ->
            setBool(FEATURES_RANDOMIZED_NAT, randomizedNat)
        }
        observeFeature(userData.safeModeLiveData) {
            safeModeValue?.let { setBool(FEATURES_SAFE_MODE, it) } ?: remove(FEATURES_SAFE_MODE)
        }
        observeFeature(userData.vpnAcceleratorLiveData) {
            setBool(FEATURES_SPLIT_TCP, splitTcpValue)
        }
    }

    private fun observeFeature(featureChange: LiveEvent, update: Features.() -> Unit) {
        features.update()
        featureChange.observeForever {
            features.update()
            agent?.setFeatures(features)
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
        if (appConfig.getFeatureFlags().netShieldEnabled) {
            val netShieldValue = userData.getNetShieldProtocol(currentUser.vpnUserCached()).ordinal.toLong()
            features.setInt(FEATURES_NETSHIELD, netShieldValue)
        }
        features.setBool(FEATURES_RANDOMIZED_NAT, userData.randomizedNatEnabled)
        safeModeValue?.let { features.setBool(FEATURES_SAFE_MODE, it) } ?: features.remove(FEATURES_SAFE_MODE)
        features.setBool(FEATURES_SPLIT_TCP, splitTcpValue)
        val bouncing = lastConnectionParams?.bouncing
        if (bouncing == null)
            features.remove(FEATURES_BOUNCING)
        else
            features.setString(FEATURES_BOUNCING, bouncing)
    }

    // Handle updates to both VpnState and local agent's state.
    private fun processCombinedState(vpnState: VpnState, localAgentState: String?) {
        val newSelfState = if (vpnProtocol.localAgentEnabled() && currentUser.sessionIdCached() != null) {
            when (vpnState) {
                VpnState.Connected -> handleLocalAgentStates(localAgentState)
                VpnState.Disabled, VpnState.Disconnecting -> {
                    closeAgentConnection()
                    vpnState
                }
                else -> vpnState
            }
        } else vpnState
        selfStateObservable.postValue(newSelfState)
    }

    private fun handleLocalAgentStates(localAgentState: String?): VpnState {
        connectToLocalAgent()
        return when (localAgentState) {
            agentConstants.stateConnected ->
                VpnState.Connected
            agentConstants.stateConnectionError,
            agentConstants.stateServerUnreachable -> {
                if (agent?.unableToConnect == true) {
                    reconnectOnLocalAgentUnreachable()
                }
                // When unreachable comes from local agent it means VPN tunnel is still active, set UNREACHABLE
                // instead of UNREACHABLE_INTERNAL to skip recovery with pings, as those won't help in this situation.
                VpnState.Error(ErrorType.UNREACHABLE)
            }
            agentConstants.stateClientCertificateExpiredError -> {
                refreshCertOnLocalAgent("local agent: certificate expired", force = false)
                VpnState.Connecting
            }
            agentConstants.stateClientCertificateUnknownCA -> {
                refreshCertOnLocalAgent("local agent: unknown CA", force = true)
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

    private fun refreshCertOnLocalAgent(reason: String, force: Boolean) {
        ProtonLogger.log(UserCertRefresh, "reason: $reason")
        selfStateObservable.postValue(VpnState.Connecting)
        closeAgentConnection()
        reconnectionJob = mainScope.launch {
            currentUser.sessionId()?.let { sessionId ->
                val result = if (force)
                    certificateRepository.updateCertificate(sessionId, false)
                else
                    certificateRepository.getCertificate(sessionId, false)
                when (result) {
                    is CertificateRepository.CertificateResult.Success ->
                        connectToLocalAgent()
                    is CertificateRepository.CertificateResult.Error -> {
                        // FIXME: eventually we'll need a more sophisticated logic that'd keep trying
                        setError(ErrorType.LOCAL_AGENT_ERROR, description = "Failed to refresh certificate")
                    }
                }
            }
        }
    }

    private fun reconnectOnLocalAgentUnreachable() {
        closeAgentConnection()
        setError(ErrorType.UNREACHABLE_INTERNAL)
    }

    @VisibleForTesting
    fun revokeCertificateAndReconnect(reason: String) {
        selfStateObservable.postValue(VpnState.Connecting)
        closeAgentConnection()
        reconnectionJob = mainScope.launch {
            currentUser.sessionId()?.let { sessionId ->
                ProtonLogger.log(UserCertRevoked, "reason: $reason")
                certificateRepository.generateNewKey(sessionId)
                when (certificateRepository.updateCertificate(sessionId, true)) {
                    is CertificateRepository.CertificateResult.Error -> {
                        // FIXME: eventually we'll need a more sophisticated logic that'd keep trying
                        setError(ErrorType.LOCAL_AGENT_ERROR, description = "Failed to refresh revoked certificate")
                    }
                    is CertificateRepository.CertificateResult.Success -> {
                        yield()
                        reconnect()
                    }
                }
            }
        }
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    fun connectToLocalAgent() {
        if (agent == null && agentConnectionJob == null) {
            val hostname = lastConnectionParams?.connectingDomain?.entryDomain
            agentConnectionJob = mainScope.launch {
                val certInfo = certificateRepository.getCertificate(currentUser.sessionId()!!)
                if (certInfo is CertificateRepository.CertificateResult.Success) {
                    // Tunnel needs a moment to become functional
                    delay(500)

                    prepareFeaturesForAgentConnection()
                    agent = createAgentConnection(certInfo, hostname, createNativeClient())
                } else {
                    setError(ErrorType.LOCAL_AGENT_ERROR, description = "Failed to get certificate")
                }
            }
        }
    }

    private fun closeAgentConnection() {
        // Don't cancel any of the jobs if there is no agent connection. Otherwise it's possible to incorrectly cancel a
        // running reconnection job.
        if (agent != null || agentConnectionJob != null) {
            reconnectionJob?.cancel()
            reconnectionJob = null
            agentConnectionJob?.cancel()
            agentConnectionJob = null
            agent?.close()
            agent = null
        }
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
        if (connectingDomain.publicKeyX25519 == null) {
            ProtonLogger.log(ConnConnectScanFailed, "no public key")
            emptyList()
        } else {
            val candidatePorts = ports.takeRandomStable(numberOfPorts)
            ProtonLogger.log(
                ConnConnectScan,
                "${connectingDomain.entryDomain}/$vpnProtocol, UDP ports: ${candidatePorts}"
            )
            candidatePorts.parallelSearch(waitForAll, priorityWaitMs = PING_PRIORITY_WAIT_DELAY) {
                pingUdp(connectingDomain.entryIp, it, connectingDomain.publicKeyX25519)
            }
        }
    }

    private fun pingUdp(ip: String, port: Int, publicKeyX25519: String?): Boolean {
        val responds = VpnPing.pingSync(ip, port.toLong(), publicKeyX25519, SCAN_TIMEOUT_MILLIS)
        if (!responds) {
            ProtonLogger.log(ConnConnectScanFailed, "destination: $ip:$port (UDP)")
        }
        return responds
    }

    companion object {
        // During this time pings will prefer ports in order in which they were defined
        const val PING_PRIORITY_WAIT_DELAY = 1000L
        private const val DISCONNECT_WAIT_TIMEOUT = 3000L
        private const val FEATURES_BOUNCING = "bouncing"
        private const val FEATURES_NETSHIELD = "netshield-level"
        private const val FEATURES_RANDOMIZED_NAT = "randomized-nat"
        private const val FEATURES_SAFE_MODE = "safe-mode"
        private const val FEATURES_SPLIT_TCP = "split-tcp"
    }
}
