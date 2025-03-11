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

import android.os.Build
import androidx.annotation.CallSuper
import androidx.annotation.VisibleForTesting
import com.proton.gopenpgp.localAgent.AgentConnection
import com.proton.gopenpgp.localAgent.Features
import com.proton.gopenpgp.localAgent.LocalAgent
import com.proton.gopenpgp.localAgent.NativeClient
import com.proton.gopenpgp.localAgent.StatusMessage
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.concurrency.VpnDispatcherProvider
import com.protonvpn.android.logging.ConnError
import com.protonvpn.android.logging.ConnStateChanged
import com.protonvpn.android.logging.LocalAgentError
import com.protonvpn.android.logging.LocalAgentStateChanged
import com.protonvpn.android.logging.LocalAgentStatus
import com.protonvpn.android.logging.LogCategory
import com.protonvpn.android.logging.ProtonLogger
import com.protonvpn.android.logging.UserCertRefresh
import com.protonvpn.android.logging.UserCertRevoked
import com.protonvpn.android.models.config.TransmissionProtocol
import com.protonvpn.android.models.config.VpnProtocol
import com.protonvpn.android.models.vpn.ConnectionParams
import com.protonvpn.android.models.vpn.Server
import com.protonvpn.android.netshield.NetShieldStats
import com.protonvpn.android.redesign.vpn.AnyConnectIntent
import com.protonvpn.android.redesign.vpn.usecases.SettingsForConnection
import com.protonvpn.android.settings.data.LocalUserSettings
import com.protonvpn.android.ui.ForegroundActivityTracker
import com.protonvpn.android.ui.home.GetNetZone
import com.protonvpn.android.utils.Constants
import com.protonvpn.android.utils.Storage
import com.protonvpn.android.utils.SyncStateFlow
import com.protonvpn.android.utils.suspendForCallbackWithTimeout
import io.sentry.Sentry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.yield
import me.proton.core.network.data.di.SharedOkHttpClient
import me.proton.core.network.domain.NetworkManager
import me.proton.core.network.domain.NetworkStatus
import okhttp3.OkHttpClient

data class PrepareResult(val backend: VpnBackend, val connectionParams: ConnectionParams) : java.io.Serializable

interface VpnBackendProvider {
    suspend fun prepareConnection(
        protocol: ProtocolSelection,
        connectIntent: AnyConnectIntent,
        server: Server,
        alwaysScan: Boolean = true
    ): PrepareResult?

    // Returns first from [preferenceList] that responded in a given time frame or null
    // [fullScanServer] when set will have all ports scanned.
    suspend fun pingAll(
        orgIntent: AnyConnectIntent,
        orgProtocol: ProtocolSelection,
        preferenceList: List<PhysicalServer>,
        fullScanServer: PhysicalServer? = null
    ): PingResult?

    data class PingResult(val physicalServer: PhysicalServer, val responses: List<PrepareResult>)
}

interface AgentConnectionInterface {
    val state: String
    val status: StatusMessage?
    val certInfo: CertificateRepository.CertificateResult.Success
    fun setFeatures(features: Features)
    fun sendGetStatus(withStatistics: Boolean)
    fun setConnectivity(connectivity: Boolean)
    fun close()
}

abstract class VpnBackend(
    val settingsForConnection: SettingsForConnection,
    val certificateRepository: CertificateRepository,
    val networkManager: NetworkManager,
    val networkCapabilitiesFlow: NetworkCapabilitiesFlow,
    val vpnProtocol: VpnProtocol,
    val mainScope: CoroutineScope,
    val dispatcherProvider: VpnDispatcherProvider,
    val localAgentUnreachableTracker: LocalAgentUnreachableTracker,
    val currentUser: CurrentUser,
    val getNetZone: GetNetZone,
    val foregroundActivityTracker: ForegroundActivityTracker,
    @SharedOkHttpClient val okHttp: OkHttpClient? = null,
    val shouldWaitForTunnelVerified: Boolean = true,
) : VpnStateSource {

    inner class VpnAgentClient : NativeClient {
        private val agentConstants = LocalAgent.constants()
        private var gatherStatsJob: Job? = null

        @Volatile
        private var isClosed: Boolean = false

        fun close() {
            isClosed = true
            gatherStatsJob?.cancel()
        }

        override fun log(msg: String) {
            ProtonLogger.logCustom(LogCategory.LOCAL_AGENT, msg)
        }

        override fun onError(code: Long, description: String) {
            mainScope.launch {
                if (isClosed) return@launch
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
                        reconnectLocalAgent(needNewCertificate = true, reason = "local agent: certificate expired")

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

                    agentConstants.errorCodeServerError,
                    agentConstants.errorCodeUnknown ->
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
        }

        override fun onState(state: String) {
            mainScope.launch {
                if (isClosed) return@launch
                ProtonLogger.log(LocalAgentStateChanged, state)
                processCombinedState(vpnProtocolState, state)
            }
        }

        override fun onStatusUpdate(status: StatusMessage) {
            mainScope.launch {
                if (isClosed) return@launch
                val stats = status.featuresStatistics?.toStats()
                if (stats != null) {
                    netShieldStatsFlow.tryEmit(
                        NetShieldStats(
                            adsBlocked = stats.getAds(),
                            trackersBlocked = stats.getTracking(),
                            savedBytes = stats.getBandwidth()
                        )
                    )
                }
                val newConnectionDetails = status.connectionDetails
                if (newConnectionDetails != null) {
                    lastKnownExitIp.value = IpPair(
                        ipV4 = newConnectionDetails.serverIpv4,
                        ipV6 = newConnectionDetails.serverIpv6?.takeIf {
                            lastConnectionParams?.enableIPv6 == true && it.isNotBlank()
                        }
                    )
                    // Local Agent's ClientIP is not accurate for secure core
                    if (lastConnectionParams?.server?.isSecureCoreServer != true) {
                        if (!newConnectionDetails.deviceIp.isNullOrBlank())
                            getNetZone.updateIp(newConnectionDetails.deviceIp)
                        if (!newConnectionDetails.deviceCountry.isNullOrBlank())
                            getNetZone.updateCountry(newConnectionDetails.deviceCountry)
                    }
                }
                ProtonLogger.log(LocalAgentStatus, status.toString())
            }
        }

        override fun onTlsSessionStarted() {
            require(gatherStatsJob == null)
            gatherStatsJob = mainScope.launch {
                combine(
                    foregroundActivityTracker.isInForegroundFlow,
                    currentUser.vpnUserFlow.map { it?.isFreeUser != true }
                ) { isInForeground, isNotFreeUser ->
                    isInForeground && isNotFreeUser
                }.collectLatest { shouldSendGetStatus ->
                    if (shouldSendGetStatus) {
                        while (true) {
                            agent?.sendGetStatus(true)
                            delay(LOCAL_AGENT_STATUS_DELAY_MS)
                        }
                    }
                }
            }
        }

        override fun onTlsSessionEnded() {
            netShieldStatsFlow.tryEmit(NetShieldStats())
            gatherStatsJob?.cancel()
            gatherStatsJob = null
        }
    }

    protected var lastConnectionParams: ConnectionParams? = null
    val lastKnownExitIp = MutableStateFlow<IpPair?>(null)
    val netShieldStatsFlow = MutableStateFlow(NetShieldStats())
    private val cachedSessionId = SyncStateFlow(mainScope, currentUser.sessionIdFlow)

    abstract suspend fun prepareForConnection(
        connectIntent: AnyConnectIntent,
        server: Server,
        transmissionProtocols: Set<TransmissionProtocol>,
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
            // Re-save last connection params, as they may be wiped on disconnect in
            // onDestroyService case
            Storage.save(lastConnectionParams, ConnectionParams::class.java)
            connect(params)
        }
    }

    open fun createAgentConnection(
        certInfo: CertificateRepository.CertificateResult.Success,
        hostname: String?,
        nativeClient: VpnAgentClient,
        features: Features,
    ) = object : AgentConnectionInterface {
        val agent = AgentConnection(
            certInfo.certificate,
            certInfo.privateKeyPem,
            Constants.VPN_ROOT_CERTS,
            Constants.LOCAL_AGENT_ADDRESS,
            hostname,
            nativeClient,
            features,
            networkManager.isConnectedToNetwork(),
            60,
            2
        )

        override val state: String get() = agent.state
        override val status: StatusMessage? get() = agent.status
        override val certInfo = certInfo

        override fun setFeatures(features: Features) {
            agent.setFeatures(features)
        }

        override fun sendGetStatus(withStatistics: Boolean) {
            agent.sendGetStatus(withStatistics)
        }

        override fun setConnectivity(connectivity: Boolean) {
            agent.setConnectivity(connectivity)
        }

        override fun close() {
            nativeClient.close()
            agent.close()
        }
    }

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

            // Wait for backend to finish closing the tunnel, otherwise it overwrites the error with the Disabled state.
            // The timeout shouldn't be necessary but in case the backend doesn't reach the Disabled state it's safer
            // to continue than get stuck.
            try {
                withTimeout(1000L) {
                    internalVpnProtocolState.first { it is VpnState.Disabled }
                }
            } catch (e: TimeoutCancellationException) {
                val status = "protocol state: $vpnProtocolState, protocol: ${lastConnectionParams?.protocolSelection}"
                Sentry.captureMessage("Timed out waiting for backend to close: $status")
            }
            selfStateFlow.value = VpnState.Error(error, description, isFinal = disconnectVPN)
        }
    }

    val internalVpnProtocolState = MutableStateFlow<VpnState>(VpnState.Disabled)

    // internalVpnProtocolState serves as a backing field for this property
    protected var vpnProtocolState: VpnState
        get() = internalVpnProtocolState.value
        set(value) {
            val hasChanged = internalVpnProtocolState.value != value
            internalVpnProtocolState.value = value
            if (hasChanged) onVpnProtocolStateChange(value)
        }

    final override val selfStateFlow = MutableStateFlow<VpnState>(VpnState.Disabled)
    private var agent: AgentConnectionInterface? = null
    private var agentConnectionJob: Job? = null
    private var reconnectionJob: Job? = null
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

    private fun initFeatures() {
        settingsForConnection
            .getFlowForCurrentConnection()
            .onEach { settings ->
                agent?.setFeatures(getFeatures(settings.connectionSettings))
            }
            .launchIn(mainScope)
    }

    private fun getFeatures(settings: LocalUserSettings) = Features().apply {
        setInt(FEATURES_NETSHIELD, settings.netShield.ordinal.toLong())
        setBool(FEATURES_RANDOMIZED_NAT, settings.randomizedNat)
        setBool(FEATURES_SPLIT_TCP, settings.vpnAccelerator)

        val bouncing = lastConnectionParams?.bouncing
        if (bouncing != null)
            setString(FEATURES_BOUNCING, bouncing)
    }

    private fun onVpnProtocolStateChange(value: VpnState) {
        mainScope.launch(start = CoroutineStart.UNDISPATCHED) {
            if (value == VpnState.Connected) {
                withContext(dispatcherProvider.Io) {
                    // TCP sockets cannot be reused after connecting to another server, force
                    // OkHttp to reset all sockets so that we avoid timeout for next request.
                    okHttp?.resetSockets()
                }
                if (shouldWaitForTunnelVerified && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                    waitForTunnelVerified()
            } else {
                lastKnownExitIp.value = null
            }
            processCombinedState(value, agent?.state)
        }
    }

    // Wait for VPN tunnel to be ready according to system before proceeding. Otherwise
    // subsequent TLS connections might be still failing on newer Android versions.
    private suspend fun waitForTunnelVerified(timeoutMs: Long = 500L) {
        withTimeoutOrNull(timeoutMs) {
            networkCapabilitiesFlow.filterNotNull().first { capabilities ->
                // If capability "VALIDATED" is present it need to be true (it's not present on
                // older androids)
                capabilities[CAPABILITY_NOT_VPN] == false && capabilities[CAPABILITY_VALIDATED] != false
            }
        }
    }

    // Handle updates to both VpnState and local agent's state.
    private fun processCombinedState(vpnState: VpnState, localAgentState: String?) {
        val newSelfState = if (
            cachedSessionId.value != null
            && lastConnectionParams?.connectIntent !is AnyConnectIntent.GuestHole
        ) {
            if (vpnState == VpnState.Connected) {
                handleLocalAgentStates(localAgentState)
            } else {
                closeAgentConnection()
                vpnState
            }
        } else vpnState
        selfStateFlow.value = newSelfState
    }

    private fun handleLocalAgentStates(localAgentState: String?): VpnState {
        connectToLocalAgent()
        return when (localAgentState) {
            agentConstants.stateConnected -> {
                localAgentUnreachableTracker.reset(true)
                VpnState.Connected
            }
            agentConstants.stateConnectionError,
            agentConstants.stateServerUnreachable -> {
                // When unreachable comes from local agent it means VPN tunnel is still active, set either
                // UNREACHABLE or UNREACHABLE_INTERNAL to fallback with pings.
                val action = localAgentUnreachableTracker.onUnreachable()
                when (action) {
                    LocalAgentUnreachableTracker.UnreachableAction.SILENT_RECONNECT ->
                        VpnState.Connected
                    LocalAgentUnreachableTracker.UnreachableAction.FALLBACK -> {
                        localAgentUnreachableTracker.onFallbackTriggered()
                        VpnState.Error(ErrorType.UNREACHABLE_INTERNAL, isFinal = false)
                    }
                    LocalAgentUnreachableTracker.UnreachableAction.ERROR ->
                        VpnState.Error(ErrorType.UNREACHABLE, isFinal = false)
                }
            }
            agentConstants.stateClientCertificateExpiredError -> {
                reconnectLocalAgent("local agent: certificate expired", needNewCertificate = true)
                VpnState.Connecting
            }
            agentConstants.stateClientCertificateUnknownCA -> {
                reconnectLocalAgent("local agent: unknown CA", needNewCertificate = true)
                VpnState.Connecting
            }
            agentConstants.stateServerCertificateError ->
                VpnState.Error(ErrorType.PEER_AUTH_FAILED, isFinal = false)
            agentConstants.stateWaitingForNetwork ->
                VpnState.WaitingForNetwork
            agentConstants.stateHardJailed, // Error will be handled in NativeClient.onError method
            agentConstants.stateSoftJailed ->
                VpnState.Connecting
            agentConstants.stateConnecting ->
                if (localAgentUnreachableTracker.isSilentReconnect())
                    VpnState.Connected
                else
                    VpnState.Connecting
            else ->
                VpnState.Connecting
        }
    }

    private fun reconnectLocalAgent(reason: String, needNewCertificate: Boolean) {
        ProtonLogger.log(UserCertRefresh, "reason: $reason")
        selfStateFlow.value = VpnState.Connecting
        // Remember current cert before it's cleared by closing connection.
        val connectionCert = agent?.certInfo
        closeAgentConnection()
        reconnectionJob = mainScope.launch {
            currentUser.sessionId()?.let { sessionId ->
                if (needNewCertificate) {
                    val certInfo = certificateRepository.getCertificate(sessionId)
                    val haveNewCert = certInfo is CertificateRepository.CertificateResult.Success &&
                        certInfo != connectionCert
                    if (!haveNewCert)
                        certificateRepository.updateCertificate(sessionId, false)
                }
                connectToLocalAgent()
            }
        }
    }

    fun revokeCertificateAndReconnect(reason: String) {
        selfStateFlow.value = VpnState.Connecting
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
                val sessionId = currentUser.sessionId()
                if (sessionId != null) { // null can happen if user just logged out
                    val certInfo = certificateRepository.getCertificate(sessionId)
                    if (certInfo is CertificateRepository.CertificateResult.Success) {
                        val settings = settingsForConnection.getFor(lastConnectionParams?.connectIntent)
                        val features = getFeatures(settings)
                        agent = createAgentConnection(certInfo, hostname, createNativeClient(), features)
                    } else {
                        setError(
                            ErrorType.LOCAL_AGENT_ERROR,
                            description = "Failed to get certificate"
                        )
                    }
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
            localAgentUnreachableTracker.reset(false)
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

    companion object {
        private const val DISCONNECT_WAIT_TIMEOUT = 3000L
        private const val LOCAL_AGENT_STATUS_DELAY_MS = 60000L
        private const val FEATURES_BOUNCING = "bouncing"
        private const val FEATURES_NETSHIELD = "netshield-level"
        private const val FEATURES_RANDOMIZED_NAT = "randomized-nat"
        private const val FEATURES_SPLIT_TCP = "split-tcp"
    }
}

private class OkHttpIdleCallbackWrapper(
    val original: Runnable?,
    val callback: () -> Unit
) : Runnable {
    override fun run() {
        original?.run()
        callback()
    }
}

private suspend fun OkHttpClient.resetSockets() {
    val haveOngoingRequests = dispatcher.runningCallsCount() + dispatcher.queuedCallsCount() > 0
    ProtonLogger.log(ConnStateChanged, "Tunnel connected: resetting OkHttp sockets, ongoing_requests=$haveOngoingRequests")

    // Wait (with timeout) for OkHttp to actually become Idle (cancelAll is asynchronous)
    if (haveOngoingRequests) {
        // Cancel all running calls
        dispatcher.cancelAll()

        val original = dispatcher.idleCallback?.unwrapIdleCallback()
        val timedOut = null == suspendForCallbackWithTimeout(
            500,
            onClose = { dispatcher.idleCallback = original },
            registerCallback = { resume ->
                dispatcher.idleCallback = OkHttpIdleCallbackWrapper(original) {
                    resume(Unit)
                }
            }
        )
        if (timedOut)
            ProtonLogger.log(ConnStateChanged, "Tunnel opened: timed-out waiting for OkHttp idle")
    }

    // Get rid of all cached connections
    connectionPool.evictAll()
}

private fun Runnable?.unwrapIdleCallback(): Runnable? =
    if (this is OkHttpIdleCallbackWrapper) original?.unwrapIdleCallback() else this
