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
import androidx.lifecycle.MutableLiveData
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
            println("### " + msg)
            Log.d(msg)
        }

        override fun onError(code: Long, description: String) {
            when (code) {
                agentConstants.errorCodeMaxSessionsBasic,
                agentConstants.errorCodeMaxSessionsFree,
                agentConstants.errorCodeMaxSessionsPlus,
                agentConstants.errorCodeMaxSessionsPro,
                agentConstants.errorCodeMaxSessionsUnknown,
                agentConstants.errorCodeMaxSessionsVisionary ->
                    selfStateObservable.postValue(VpnState.Error(ErrorType.MAX_SESSIONS))
                agentConstants.errorCodeCertificateRevoked,
                agentConstants.errorCodeKeyUsedMultipleTimes ->
                    revokeCertificateAndReconnect()
                agentConstants.errorCodeCertificateExpired -> refreshCertOnLocalAgent()
                agentConstants.errorCodePolicyViolation1 ->
                    selfStateObservable.postValue(
                        VpnState.Error(ErrorType.LOCAL_AGENT_ERROR, "Policy violation")
                    )
                agentConstants.errorCodeUserBadBehavior ->
                    selfStateObservable.postValue(
                        VpnState.Error(ErrorType.LOCAL_AGENT_ERROR, "Bad behaviour")
                    )
            }
            Log.e("error: " + code)
            Log.e("description: " + description)
        }

        override fun onState(state: String) {
            println("### state $state")
            selfStateObservable.postValue(getGlobalVpnState(vpnProtocolState, state))
        }
    }

    protected var vpnProtocolState: VpnState = VpnState.Disabled
        set(value) {
            field = value
            selfStateObservable.postValue(getGlobalVpnState(value, agent?.state))
        }

    override val selfStateObservable = MutableLiveData<VpnState>(VpnState.Disabled)
    private var agent: AgentConnection? = null
    private var agentConnectionJob: Job? = null
    private var reconnectionJob: Job? = null
    private var features: Features = Features()
    private val agentConstants = localAgent.LocalAgent.constants()

    init {
        mainScope.launch {
            networkManager.observe().collect { status ->
                agent?.setConnectivity(status != NetworkStatus.Disconnected)
            }
        }

        userData.netShieldLiveData.observeForever {
            it?.let {
                features.setInt(FEATURES_NETSHIELD, it.ordinal.toLong())
                agent?.setFeatures(features)
            }
        }
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
        if (agent == null && agentConnectionJob == null) {
            connectToLocalAgent()
        }
        return when (localAgentState) {
            agentConstants.stateConnected -> VpnState.Connected
            agentConstants.stateConnectionError -> VpnState.Error(ErrorType.UNREACHABLE)
            agentConstants.stateServerUnreachable -> VpnState.Error(ErrorType.UNREACHABLE)
            agentConstants.stateSoftJailed -> VpnState.Connecting
            agentConstants.stateClientCertificateError -> VpnState.Connecting
            agentConstants.stateServerCertificateError -> VpnState.Connecting
            agentConstants.stateHardJailed -> VpnState.Connecting
            else -> VpnState.Connecting
        }
    }

    private fun refreshCertOnLocalAgent() {
        reconnectionJob = mainScope.launch {
            certificateRepository.updateCertificate(userData.sessionId!!, false)
            closeAgentConnection()
            connectToLocalAgent()
        }
    }

    private fun revokeCertificateAndReconnect() {
        selfStateObservable.postValue(VpnState.Connecting)
        reconnectionJob = mainScope.launch {
            certificateRepository.generateNewKey(userData.sessionId!!)
            certificateRepository.updateCertificate(userData.sessionId!!, false)
            yield()
            reconnect()
        }
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    fun connectToLocalAgent() {
        val hostname = lastConnectionParams?.connectingDomain?.entryDomain
        agentConnectionJob = mainScope.launch {
            val certInfo = certificateRepository.getCertificate(userData.sessionId!!)
            delay(500)
            if (certInfo is CertificateRepository.CertificateResult.Success) {
                features.setInt(FEATURES_NETSHIELD, userData.netShieldProtocol.ordinal.toLong())
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
                selfStateObservable.postValue(
                    VpnState.Error(
                        ErrorType.LOCAL_AGENT_ERROR,
                        "Failed to get wireguard certificate"
                    )
                )
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
    }
}
