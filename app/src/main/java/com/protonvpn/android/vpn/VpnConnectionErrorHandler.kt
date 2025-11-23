/*
 * Copyright (c) 2021 Proton AG
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

import com.protonvpn.android.api.ProtonApiRetroFit
import com.protonvpn.android.appconfig.AppConfig
import com.protonvpn.android.auth.data.VpnUser
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.di.ElapsedRealtimeClock
import com.protonvpn.android.logging.ConnServerSwitchFailed
import com.protonvpn.android.logging.ConnServerSwitchServerSelected
import com.protonvpn.android.logging.ConnServerSwitchTrigger
import com.protonvpn.android.logging.LogCategory
import com.protonvpn.android.logging.ProtonLogger
import com.protonvpn.android.logging.toLog
import com.protonvpn.android.models.config.VpnProtocol
import com.protonvpn.android.servers.api.ConnectingDomain
import com.protonvpn.android.models.vpn.ConnectionParams
import com.protonvpn.android.servers.Server
import com.protonvpn.android.models.vpn.usecase.GetConnectingDomain
import com.protonvpn.android.redesign.CountryId
import com.protonvpn.android.redesign.vpn.AnyConnectIntent
import com.protonvpn.android.redesign.vpn.ConnectIntent
import com.protonvpn.android.redesign.vpn.ServerFeature
import com.protonvpn.android.redesign.vpn.usecases.SettingsForConnection
import com.protonvpn.android.servers.ServerManager2
import com.protonvpn.android.settings.data.LocalUserSettings
import com.protonvpn.android.ui.home.ServerListUpdater
import com.protonvpn.android.utils.UserPlanManager
import com.protonvpn.android.utils.UserPlanManager.InfoChange.PlanChange
import com.protonvpn.android.utils.UserPlanManager.InfoChange.UserBecameDelinquent
import com.protonvpn.android.utils.UserPlanManager.InfoChange.VpnCredentials
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import me.proton.core.network.domain.ApiResult
import me.proton.core.network.domain.HttpResponseCodes
import me.proton.core.network.domain.NetworkManager
import java.io.Serializable
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

sealed class SwitchServerReason : Serializable {

    data class Downgrade(val fromTier: String, val toTier: String) : SwitchServerReason()
    object UserBecameDelinquent : SwitchServerReason()
    object ServerInMaintenance : SwitchServerReason()
    object ServerUnreachable : SwitchServerReason()
    object ServerUnavailable : SwitchServerReason()
    object UnknownAuthFailure : SwitchServerReason()

    // Used in logging, provides readable names for the objects.
    override fun toString() = this::class.java.simpleName
}

sealed class VpnFallbackResult {

    sealed class Switch : VpnFallbackResult() {

        abstract val log: String
        abstract val reason: SwitchServerReason?
        abstract val notifyUser: Boolean
        abstract val fromServer: Server?
        abstract val toServer: Server

        data class SwitchConnectIntent(
            override val fromServer: Server?,
            override val toServer: Server,
            val fromConnectIntent: ConnectIntent,
            val toConnectIntent: ConnectIntent,
            override val reason: SwitchServerReason? = null,
        ) : Switch() {
            override val notifyUser = reason != null
            override val log get() = "SwitchConnectIntent: ${toConnectIntent.toLog()} reason: $reason"
        }

        data class SwitchServer(
            override val fromServer: Server?,
            val connectIntent: ConnectIntent,
            val preparedConnection: PrepareResult,
            override val reason: SwitchServerReason,
            val compatibleProtocol: Boolean,
            val switchedSecureCore: Boolean,
            override val notifyUser: Boolean,
        ) : Switch() {
            override val toServer get() = preparedConnection.connectionParams.server
            override val log get() = "SwitchServer ${preparedConnection.connectionParams.info} " +
                "reason: $reason compatibleProtocol: $compatibleProtocol"
        }
    }

    data class Error(
        val originalParams: ConnectionParams,
        val type: ErrorType,
        val reason: SwitchServerReason?
    ) : VpnFallbackResult()
}

data class PhysicalServer(val server: Server, val connectingDomain: ConnectingDomain)

class StuckConnectionHandler(val elapsedMs: () -> Long) {

    private var stuckStarted : Long = 0
    private var stuckConnection : ConnectionParams? = null

    fun onSwitchIgnoredOnCurrentConnection(current: ConnectionParams) {
        if (stuckConnection != current) {
            stuckStarted = elapsedMs()
            stuckConnection = current
        }
    }

    fun isStuckOn(connection: ConnectionParams?) =
        connection != null && connection == stuckConnection
            && elapsedMs() - stuckStarted >= STUCK_DURATION_MS

    fun reset() {
        stuckStarted = 0
        stuckConnection = null
    }

    companion object {
        val STUCK_DURATION_MS = TimeUnit.MINUTES.toMillis(1)
    }
}

@Singleton
class VpnConnectionErrorHandler @Inject constructor(
    scope: CoroutineScope,
    private val api: ProtonApiRetroFit,
    private val appConfig: AppConfig,
    private val settingsForConnection: SettingsForConnection,
    private val userPlanManager: UserPlanManager,
    private val serverManager: ServerManager2,
    private val stateMonitor: VpnStateMonitor,
    private val serverListUpdater: ServerListUpdater,
    private val networkManager: NetworkManager,
    private val vpnBackendProvider: dagger.Lazy<VpnBackendProvider>,
    private val currentUser: CurrentUser,
    private val getConnectingDomain: GetConnectingDomain,
    private val getOnlineServersForIntent: GetOnlineServersForIntent,
    @ElapsedRealtimeClock private val elapsedMs: () -> Long,
    @Suppress("unused") errorUIManager: VpnErrorUIManager // Forces creation of a VpnErrorUiManager instance.
) {
    private var handlingAuthError = false
    private val stuckHandler = StuckConnectionHandler(elapsedMs)
    private var lastServerErrorHandledMs : Long? = null

    val switchConnectionFlow = MutableSharedFlow<VpnFallbackResult>()

    init {
        userPlanManager.infoChangeFlow.onEach { changes ->
            if (!handlingAuthError && stateMonitor.isEstablishingOrConnected) {
                val params = stateMonitor.connectionParams!!
                val connectIntent = params.connectIntent
                if (connectIntent is ConnectIntent) { // Don't fall back for Guest Hole connections.
                    getCommonFallbackForInfoChanges(
                        connectIntent,
                        params.server,
                        changes,
                        currentUser.vpnUser()
                    )?.let {
                        switchConnectionFlow.emit(it)
                    }
                }
            }
        }.launchIn(scope)

        stateMonitor.status.onEach {
            // We're no longer stuck if we connected, disconnected or waiting for network.
            if (it.state == VpnState.Connected || it.state == VpnState.Disabled || it.state == VpnState.WaitingForNetwork)
                stuckHandler.reset()
        }.launchIn(scope)
    }

    @SuppressWarnings("ReturnCount")
    private suspend fun getCommonFallbackForInfoChanges(
        currentIntent: ConnectIntent,
        currentServer: Server,
        changes: List<UserPlanManager.InfoChange>,
        vpnUser: VpnUser?
    ): VpnFallbackResult.Switch? {
        val fallbackIntent = ConnectIntent.Default
        val protocol = settingsForConnection.getFor(fallbackIntent).protocol
        val fallbackServer =
            serverManager.getBestServerForConnectIntent(fallbackIntent, vpnUser, protocol)
                ?.takeIf { it.online }
                ?: return null
        for (change in changes) when {
            change is PlanChange && change.isDowngrade -> {
                return VpnFallbackResult.Switch.SwitchConnectIntent(
                    fromServer = currentServer,
                    toServer = fallbackServer,
                    fromConnectIntent = currentIntent,
                    toConnectIntent = fallbackIntent,
                    SwitchServerReason.Downgrade(change.oldUser.userTierName, change.newUser.userTierName)
                )
            }
            change is UserBecameDelinquent ->
                return VpnFallbackResult.Switch.SwitchConnectIntent(
                    fromServer = currentServer,
                    toServer = fallbackServer,
                    fromConnectIntent = currentIntent,
                    toConnectIntent = fallbackIntent,
                    SwitchServerReason.UserBecameDelinquent
                )
            else -> {}
        }
        return null
    }

    enum class CompatibilityAspect {
        Features, Tier, Gateway, City, State, Country, SecureCore
    }

    suspend fun onServerNotAvailable(connectIntent: AnyConnectIntent) =
        fallbackToCompatibleServer(connectIntent, null, false, SwitchServerReason.ServerUnavailable)

    suspend fun onServerInMaintenance(connectIntent: AnyConnectIntent, connectionParams: ConnectionParams?) =
        fallbackToCompatibleServer(
            connectIntent, connectionParams, false, SwitchServerReason.ServerInMaintenance
        )

    suspend fun onUnreachableError(connectionParams: ConnectionParams): VpnFallbackResult =
        fallbackToCompatibleServer(
            connectionParams.connectIntent,
            connectionParams,
            true,
            SwitchServerReason.ServerUnreachable
        ) ?: VpnFallbackResult.Error(connectionParams, ErrorType.UNREACHABLE, SwitchServerReason.ServerUnreachable)

    suspend fun onServerError(connectionParams: ConnectionParams): VpnFallbackResult {
        val sinceLastHandled = lastServerErrorHandledMs?.let { elapsedMs() - it }
        if (sinceLastHandled != null && sinceLastHandled < SERVER_ERROR_COOLDOWN_MS) {
            ProtonLogger.log(ConnServerSwitchFailed, "Server error cooldown")
            return VpnFallbackResult.Error(connectionParams, ErrorType.UNREACHABLE, SwitchServerReason.ServerUnreachable)
        }
        val fallback = fallbackToCompatibleServer(
            connectionParams.connectIntent,
            connectionParams,
            false,
            SwitchServerReason.ServerUnreachable
        )
        if (fallback != null)
            lastServerErrorHandledMs = elapsedMs()
        return fallback ?: VpnFallbackResult.Error(connectionParams, ErrorType.UNREACHABLE, SwitchServerReason.ServerUnreachable)
    }

    private suspend fun fallbackToCompatibleServer(
        orgIntent: AnyConnectIntent,
        orgParams: ConnectionParams?,
        includeOriginalServer: Boolean,
        reason: SwitchServerReason
    ): VpnFallbackResult.Switch? {
        if (orgIntent !is ConnectIntent) {
            ProtonLogger.logCustom(LogCategory.CONN_SERVER_SWITCH, "Ignoring reconnection for Guest Hole")
            return null
        }

        ProtonLogger.log(ConnServerSwitchTrigger, "reason: $reason")
        if (!networkManager.isConnectedToNetwork()) {
            ProtonLogger.log(ConnServerSwitchFailed, "No internet: aborting fallback")
            return null
        }

        if (serverListUpdater.needsUpdate()) {
            serverListUpdater.updateServerList()
        }

        val settingsForOrgIntent = settingsForConnection.getFor(orgIntent)
        val vpnUser = currentUser.vpnUser()
        val orgPhysicalServer =
            orgParams?.connectingDomain?.let { PhysicalServer(orgParams.server, it) }?.takeIf { it.exists() }
        val protocol: ProtocolSelection = orgParams?.protocolSelection ?: settingsForOrgIntent.protocol
        val isStuckOnCurrentServer = stuckHandler.isStuckOn(orgParams)
        if (includeOriginalServer && isStuckOnCurrentServer) {
            ProtonLogger.logCustom(
                LogCategory.CONN_SERVER_SWITCH,
                "Stuck on ${orgParams?.server?.serverName}, looking for alternative..."
            )
        }
        val considerOriginalServer = includeOriginalServer && !isStuckOnCurrentServer
        val candidates =
            getCandidateServers(orgIntent, orgPhysicalServer, protocol, vpnUser, considerOriginalServer, settingsForOrgIntent)

        candidates.forEach {
            ProtonLogger.logCustom(
                LogCategory.CONN_SERVER_SWITCH,
                "Fallback server: ${it.connectingDomain.entryDomain} city=${it.server.city}"
            )
        }

        val pingResult = vpnBackendProvider.get().pingAll(orgIntent, protocol, candidates, orgPhysicalServer) ?: run {
            ProtonLogger.log(ConnServerSwitchFailed, "No server responded")
            return null
        }

        // Original server + protocol responded, don't switch
        if (
            pingResult.physicalServer == orgPhysicalServer &&
            pingResult.responses.any { it.connectionParams.hasSameProtocolParams(orgParams) }
        ) {
            stuckHandler.onSwitchIgnoredOnCurrentConnection(orgParams)
            ProtonLogger.log(
                ConnServerSwitchServerSelected,
                "Got response for current connection - don't switch VPN server"
            )
            return null
        }

        val expectedProtocolConnection = pingResult.getExpectedProtocolConnection(settingsForOrgIntent.protocol)
        val orgDirectServerId =
            (orgIntent as? ConnectIntent.Server)?.serverId ?: (orgIntent as? ConnectIntent.Gateway)?.serverId
        val orgDirectServer = orgDirectServerId?.let { serverManager.getServerById(it) }
        val score = getServerScore(pingResult.physicalServer.server, orgIntent, orgDirectServer, vpnUser)
        val switchedSecureCore = orgIntent.isSecureCore() && !hasCompatibility(score, CompatibilityAspect.SecureCore)
        val isCompatible = isCompatibleServer(score, pingResult.physicalServer, orgPhysicalServer) &&
            expectedProtocolConnection != null && !switchedSecureCore


        ProtonLogger.log(
            ConnServerSwitchServerSelected,
            with(pingResult.physicalServer) { "${server.serverName} ${connectingDomain.entryDomain}" }
        )
        return VpnFallbackResult.Switch.SwitchServer(
            orgParams?.server,
            orgIntent,
            expectedProtocolConnection ?: pingResult.responses.first(),
            reason,
            notifyUser = !isCompatible,
            compatibleProtocol = expectedProtocolConnection != null,
            switchedSecureCore = switchedSecureCore
        )
    }

    private fun hasCompatibility(score: Int, aspect: CompatibilityAspect) =
        score and (1 shl aspect.ordinal) != 0

    private fun isCompatibleServer(score: Int, physicalServer: PhysicalServer, orgPhysicalServer: PhysicalServer?) =
        hasCompatibility(score, CompatibilityAspect.Gateway) ||
        hasCompatibility(score, CompatibilityAspect.Country) &&
        hasCompatibility(score, CompatibilityAspect.Features) &&
        hasCompatibility(score, CompatibilityAspect.SecureCore) &&
        (hasCompatibility(score, CompatibilityAspect.Tier) ||
            orgPhysicalServer == null || physicalServer.server.tier >= orgPhysicalServer.server.tier)

    // Return first response that's has expected protocol.
    private fun VpnBackendProvider.PingResult.getExpectedProtocolConnection(
        expectedProtocol: ProtocolSelection
    ): PrepareResult? {
        if (expectedProtocol.vpn == VpnProtocol.Smart)
            return responses.first()

        return responses.firstOrNull { it.connectionParams.protocolSelection == expectedProtocol }
    }

    private suspend fun getCandidateServers(
        orgIntent: ConnectIntent,
        orgPhysicalServer: PhysicalServer?,
        protocol: ProtocolSelection,
        vpnUser: VpnUser?,
        includeOrgServer: Boolean,
        settingsForOrgIntent: LocalUserSettings
    ): List<PhysicalServer> {
        val candidateList = mutableListOf<PhysicalServer>()
        if (orgPhysicalServer != null && includeOrgServer)
            candidateList += orgPhysicalServer

        val secureCoreExpected = orgIntent.isSecureCore()
        // For profiles we allow switching only to servers compatible with its connect intent
        val eligibleOnlineServers = if (orgIntent.profileId != null) {
            getOnlineServersForIntent(orgIntent, settingsForOrgIntent.protocol, vpnUser?.maxTier ?: VpnUser.FREE_TIER)
        } else {
            val gatewayName = (orgIntent as? ConnectIntent.Gateway)?.gatewayName
            serverManager.getOnlineAccessibleServers(secureCoreExpected, gatewayName, vpnUser, protocol)
        }
        val orgIsTor = orgPhysicalServer?.server?.isTor == true
        val orgEntryIp = orgPhysicalServer?.connectingDomain?.getEntryIp(protocol)
        val scoredServers = sortServersByScore(eligibleOnlineServers, orgIntent, vpnUser).filter { candicate ->
            val ipCondition = orgPhysicalServer == null ||
                getConnectingDomain.online(candicate, protocol).any { domain ->
                    domain.getEntryIp(protocol) != orgEntryIp
                }
            val torCondition = orgIsTor || !candicate.isTor
            ipCondition && torCondition
        }

        candidateList += scoredServers
            .asSequence()
            .mapNotNull { server ->
                getConnectingDomain.online(server, protocol).filter {
                    // Ignore connecting domains with the same IP as current connection.
                    it.getEntryIp(protocol) != orgPhysicalServer?.connectingDomain?.getEntryIp(protocol)
                }.randomOrNull()?.let { connectingDomain ->
                    PhysicalServer(server, connectingDomain)
                }
            }
            .distinctBy { it.connectingDomain.entryDomain }
            .take(FALLBACK_SERVERS_COUNT - candidateList.size)
            .toList()

        val fallbacks = mutableListOf<Server>()
        val exitCountries = candidateList.map { it.server.exitCountry }.toSet()

        // All servers from the same country
        if (exitCountries.size == 1) {
            val country = exitCountries.first()

            // All servers from the same city, add different city
            val cities = candidateList.map { it.server.city }.toSet()
            if (cities.size == 1) {
                val city = cities.first()
                scoredServers.firstOrNull { it.exitCountry == country && it.city != city }?.let {
                    fallbacks += it
                }
            }

            // Add server from different country
            scoredServers.firstOrNull { it.exitCountry != country }?.let {
                fallbacks += it
            }
        }

        // For secure core add best scoring non-secure server as a last resort fallback
        if (secureCoreExpected && orgIntent.profileId == null) {
            sortServersByScore(serverManager.getOnlineAccessibleServers(false, null, vpnUser, protocol), orgIntent, vpnUser)
                .firstOrNull()
                ?.let { fallbacks += it }
        }

        return candidateList.take(FALLBACK_SERVERS_COUNT - fallbacks.size) + fallbacks.mapNotNull { server ->
            getConnectingDomain.random(server, protocol)?.let {
                PhysicalServer(server, it)
            }
        }
    }

    private inline fun scoreForCondition(value: Int, predicate: () -> Boolean): Int =
        if (predicate()) value else 0

    fun getCountryScore(country: CountryId, server: Server): Int =
        scoreForCondition(1 shl CompatibilityAspect.Country.ordinal) {
            country.isFastest || country.countryCode == server.exitCountry
        }

    fun getCityScore(cityEn: String?, server: Server): Int =
        scoreForCondition(1 shl CompatibilityAspect.City.ordinal) {
            !server.city.isNullOrEmpty() && cityEn == server.city
        }

    fun getStateScore(stateEn: String?, server: Server): Int =
        scoreForCondition(1 shl CompatibilityAspect.State.ordinal) {
            !server.state.isNullOrEmpty() && stateEn == server.state
        }

    fun getGatewayScore(gatewayName: String, server: Server): Int =
        scoreForCondition(1 shl CompatibilityAspect.Gateway.ordinal) {
            server.gatewayName == gatewayName
        }

    private fun getServerScore(
        server: Server,
        orgIntent: AnyConnectIntent,
        orgDirectServer: Server?,
        vpnUser: VpnUser?,
    ): Int {
        var score = 0
        when (orgIntent) {
            is ConnectIntent.FastestInCountry -> {
                score += getCountryScore(orgIntent.country, server)
            }
            is ConnectIntent.FastestInCity -> {
                score += getCountryScore(orgIntent.country, server)
                score += getCityScore(orgIntent.cityEn, server)
            }
            is ConnectIntent.FastestInState -> {
                score += getCountryScore(orgIntent.country, server)
                score += getStateScore(orgIntent.stateEn, server)
            }
            is ConnectIntent.SecureCore -> {
                score += getCountryScore(orgIntent.exitCountry, server)
            }
            is ConnectIntent.Gateway -> {
                // getOnlineAccessibleServers always chooses servers from the same gateway so it's always a match.
                score += getGatewayScore(orgIntent.gatewayName, server)
                if (orgDirectServer != null) {
                    score += getCountryScore(CountryId(orgDirectServer.exitCountry), server)
                    score += getCityScore(orgDirectServer.city, server)
                }
            }
            is ConnectIntent.Server -> {
                if (orgDirectServer != null) {
                    score += getCountryScore(CountryId(orgDirectServer.exitCountry), server)
                    score += getCityScore(orgDirectServer.city, server)
                }
            }
            is AnyConnectIntent.GuestHole -> {}
        }

        val serverPlusOrAbove = server.tier >= VpnUser.PLUS_TIER
        if (vpnUser?.isUserPlusOrAbove == serverPlusOrAbove) {
            // Prefer plus servers for plus users
            score += 1 shl CompatibilityAspect.Tier.ordinal
        }

        if (ServerFeature.fromServer(server).containsAll(orgIntent.features))
            score += 1 shl CompatibilityAspect.Features.ordinal

        if (orgIntent.isSecureCore() == server.isSecureCoreServer)
            score += 1 shl CompatibilityAspect.SecureCore.ordinal

        return score
    }

    private suspend fun sortServersByScore(
        servers: List<Server>,
        connectIntent: AnyConnectIntent,
        vpnUser: VpnUser?,
    ): List<Server> {
        val orgDirectServer = (connectIntent as? ConnectIntent.Server)?.serverId?.let { serverManager.getServerById(it) }
        return sortByScore(servers) { getServerScore(it, connectIntent, orgDirectServer, vpnUser) }
    }

    private fun <T> sortByScore(servers: List<T>, scoreFun: (T) -> Int) =
        servers.map { Pair(it, scoreFun(it)) }.sortedByDescending { it.second }.map { it.first }

    @SuppressWarnings("ReturnCount")
    suspend fun onAuthError(connectionParams: ConnectionParams): VpnFallbackResult {
        val orgIntent = connectionParams.connectIntent
        if (orgIntent !is ConnectIntent)
            return VpnFallbackResult.Error(connectionParams, ErrorType.AUTH_FAILED, SwitchServerReason.UnknownAuthFailure)
        try {
            handlingAuthError = true
            val vpnInfo = currentUser.vpnUser()
            userPlanManager.refreshVpnInfo()
            val newVpnInfo = currentUser.vpnUser()
            if (vpnInfo != null && newVpnInfo != null) {
                userPlanManager.computeUserInfoChanges(vpnInfo, newVpnInfo).let { infoChanges ->
                    val vpnUser = currentUser.vpnUser()
                    val fallback = getCommonFallbackForInfoChanges(orgIntent, connectionParams.server, infoChanges, vpnUser)
                    if (fallback != null)
                        return fallback

                    if (VpnCredentials in infoChanges) {
                        // Now that credentials are refreshed we can try reconnecting.
                        return with(connectionParams) {
                            VpnFallbackResult.Switch.SwitchConnectIntent(server, server, orgIntent, orgIntent)
                        }
                    }

                    val maxSessions = requireNotNull(vpnUser).maxConnect
                    val sessionCount = api.getSession().valueOrNull?.sessionList?.size ?: 0
                    if (maxSessions <= sessionCount)
                        return VpnFallbackResult.Error(connectionParams, ErrorType.MAX_SESSIONS, reason = null)
                }
            }

            return getMaintenanceFallback(connectionParams)
                // We couldn't establish if server is in maintenance, attempt searching for fallback anyway. Include
                // current connection in the search.
                ?: fallbackToCompatibleServer(
                    connectionParams.connectIntent, connectionParams, true, SwitchServerReason.UnknownAuthFailure)
                ?: VpnFallbackResult.Error(connectionParams, ErrorType.AUTH_FAILED, SwitchServerReason.UnknownAuthFailure)
        } finally {
            handlingAuthError = false
        }
    }

    private suspend fun getMaintenanceFallback(connectionParams: ConnectionParams): VpnFallbackResult? {
        if (!appConfig.isMaintenanceTrackerEnabled())
            return null

        ProtonLogger.logCustom(LogCategory.CONN_SERVER_SWITCH, "Checking if server is not in maintenance")
        val domainId = connectionParams.connectingDomain?.id ?: return null
        val result = api.getConnectingDomain(domainId)
        var findNewServer = false
        when {
            result is ApiResult.Success -> {
                val connectingDomain = result.value.connectingDomain
                if (!connectingDomain.isOnline) {
                    ProtonLogger.logCustom(
                        LogCategory.CONN_SERVER_SWITCH,
                        "Current server is in maintenance (${connectingDomain.entryDomain})"
                    )
                    serverManager.updateServerDomainStatus(connectingDomain)
                    serverListUpdater.updateServerList()
                    findNewServer = true
                }
            }

            result is ApiResult.Error.Http && result.httpCode == HttpResponseCodes.HTTP_UNPROCESSABLE -> {
                serverListUpdater.updateServerList()
                findNewServer = true
            }
        }
        return if (findNewServer)
            onServerInMaintenance(connectionParams.connectIntent, connectionParams)
                ?: connectionParams.connectIntent.profileId?.let {
                    VpnFallbackResult.Error(
                        connectionParams,
                        type = ErrorType.NO_PROFILE_FALLBACK_AVAILABLE,
                        reason = SwitchServerReason.ServerInMaintenance
                    )
                }
        else null
    }

    private suspend fun PhysicalServer.exists(): Boolean =
        serverManager.getServerById(server.serverId)?.connectingDomains?.contains(connectingDomain) == true

    suspend fun maintenanceCheck() {
        stateMonitor.connectionParams?.let { params ->
            getMaintenanceFallback(params)?.let {
                switchConnectionFlow.emit(it)
            }
        }
    }

    private fun AnyConnectIntent.isSecureCore() = this is ConnectIntent.SecureCore

    companion object {
        private const val FALLBACK_SERVERS_COUNT = 5
        val SERVER_ERROR_COOLDOWN_MS = TimeUnit.MINUTES.toMillis(2)
    }
}
