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

    abstract suspend fun connect()
    abstract suspend fun disconnect()
    abstract suspend fun reconnect()
    abstract val retryInfo: RetryInfo?

    private val nativeClient = object : NativeClient {
        override fun log(msg: String) {
            Log.d(msg)
        }

        override fun onError(code: Long, description: String) {
            Log.e("error: " + code)
            Log.e("description: " + description)
        }

        override fun onState(state: String) {
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
        if (agent == null) {
            connectToLocalAgent()
        }
        return when (localAgentState) {
            agentConstants.stateConnected -> VpnState.Connected
            // TODO Handle remaining branches and localAgentErrors here
            else -> VpnState.Connecting
        }
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    fun connectToLocalAgent() {
        agentConnectionJob = mainScope.launch {
            val certInfo = certificateRepository.getCertificate(userData.sessionId!!)
            if (certInfo is CertificateRepository.CertificateResult.Success) {
                features.setInt(FEATURES_NETSHIELD, userData.netShieldProtocol.ordinal.toLong())
                agent = AgentConnection(
                    certInfo.certificate,
                    certInfo.privateKeyPem,
                    Constants.VPN_ROOT_CERTS,
                    Constants.LOCAL_AGENT_ADDRESS,
                    nativeClient,
                    features,
                    networkManager.isConnectedToNetwork()
                )
            } else {
                Log.e("cert fetch failed") // TODO handle cert fetch failure
            }
        }
    }

    private fun closeAgentConnection() {
        agentConnectionJob?.cancel()
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
