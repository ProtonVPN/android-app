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
import com.protonvpn.android.models.vpn.ConnectionParams
import com.protonvpn.android.models.vpn.Server
import com.protonvpn.android.utils.Constants
import com.protonvpn.android.utils.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.yield
import localAgent.AgentConnection
import localAgent.Features
import localAgent.NativeClient
import me.proton.core.network.domain.NetworkManager
import me.proton.core.network.domain.NetworkStatus

data class RetryInfo(val timeoutSeconds: Int, val retryInSeconds: Int)

data class PrepareResult(val backend: VpnBackend, val connectionParams: ConnectionParams) : java.io.Serializable

interface VpnBackendProvider {
    suspend fun prepareConnection(protocol: VpnProtocol, profile: Profile, server: Server): PrepareResult?

    // Returns first from [preferenceList] that responded in a given time frame or null
    // [fullScanServer] when set will have all ports scanned.
    suspend fun pingAll(preferenceList: List<PhysicalServer>, fullScanServer: PhysicalServer? = null): PingResult?
    data class PingResult(val profile: Profile, val physicalServer: PhysicalServer, val responses: List<PrepareResult>)
}

abstract class VpnBackend(
    val userData: UserData,
    val appConfig: AppConfig,
    val certificateRepository: CertificateRepository,
    private val networkManager: NetworkManager,
    val vpnProtocol: VpnProtocol,
    val mainScope: CoroutineScope
) : VpnStateSource {

    abstract suspend fun prepareForConnection(
        profile: Profile,
        server: Server,
        scan: Boolean,
        numberOfPorts: Int = Int.MAX_VALUE // Max number of ports to be scanned
    ): List<PrepareResult>

    protected var lastConnectionParams: ConnectionParams? = null

    @CallSuper
    open suspend fun connect(connectionParams: ConnectionParams) {
        lastConnectionParams = connectionParams
    }

    abstract suspend fun disconnect()
    abstract suspend fun reconnect()
    abstract val retryInfo: RetryInfo?

    private val nativeClient = object : NativeClient {
        override fun log(msg: String) {
            Log.d(msg)
        }

        override fun onError(code: Long, description: String) {
            Log.e("Local agent description: $description")
            Log.e("Local agent error: $code")
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
                    refreshCertOnLocalAgent()

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
                    setAuthError("Restricted server")
            }
        }

        override fun onState(state: String) {
            Log.d("Local agent state: $state")
            selfStateObservable.postValue(getGlobalVpnState(vpnProtocolState, state))
        }

        override fun onStatusUpdate(status: localAgent.StatusMessage) {}
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
    private var agent: AgentConnection? = null
    private var agentConnectionJob: Job? = null
    private var reconnectionJob: Job? = null
    private val features: Features = Features()
    private val agentConstants = localAgent.LocalAgent.constants()

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
            agentConstants.stateClientCertificateError -> {
                refreshCertOnLocalAgent()
                VpnState.Connecting
            }
            agentConstants.stateServerCertificateError ->
                VpnState.Error(ErrorType.PEER_AUTH_FAILED)
            agentConstants.stateHardJailed, // Error will be handled in NativeClient.onError method
            agentConstants.stateSoftJailed,
            agentConstants.stateConnecting ->
                VpnState.Connecting
            else ->
                VpnState.Connecting
        }
    }

    private fun refreshCertOnLocalAgent() {
        selfStateObservable.postValue(VpnState.Connecting)
        closeAgentConnection()
        reconnectionJob = mainScope.launch {
            when (certificateRepository.updateCertificate(userData.sessionId!!, true)) {
                is CertificateRepository.CertificateResult.Success ->
                    connectToLocalAgent()
                is CertificateRepository.CertificateResult.Error -> {
                    // FIXME: eventually we'll need a more sophisticated logic that'd keep trying
                    Log.e("Failed to refresh certificate")
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
                    Log.e("Failed to revoke and refresh certificate")
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
                    agent = AgentConnection(
                        certInfo.certificate,
                        certInfo.privateKeyPem,
                        Constants.VPN_ROOT_CERTS,
                        Constants.LOCAL_AGENT_ADDRESS,
                        hostname,
                        nativeClient,
                        features,
                        networkManager.isConnectedToNetwork()
                    )
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

    var active = false

    companion object {
        private const val DISCONNECT_WAIT_TIMEOUT = 3000L
        private const val FEATURES_NETSHIELD = "netshield-level"
        private const val FEATURES_SPLIT_TCP = "split-tcp"
        private const val FEATURES_BOUNCING = "bouncing"
    }
}
