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

package com.protonvpn.android.vpn

import android.content.Context
import com.protonvpn.android.api.ProtonApiRetroFit
import com.protonvpn.android.appconfig.AppConfig
import com.protonvpn.android.models.config.UserData
import com.protonvpn.android.models.config.VpnProtocol
import com.protonvpn.android.models.profiles.Profile
import com.protonvpn.android.models.vpn.ConnectingDomain
import com.protonvpn.android.models.vpn.ConnectionParams
import com.protonvpn.android.models.vpn.Server
import com.protonvpn.android.ui.home.ServerListUpdater
import com.protonvpn.android.utils.ProtonLogger
import com.protonvpn.android.utils.ServerManager
import com.protonvpn.android.utils.UserPlanManager
import com.protonvpn.android.utils.UserPlanManager.InfoChange.PlanChange
import com.protonvpn.android.utils.UserPlanManager.InfoChange.VpnCredentials
import com.protonvpn.android.utils.UserPlanManager.InfoChange.UserBecameDelinquent
import io.sentry.event.EventBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import me.proton.core.network.domain.ApiResult
import me.proton.core.network.domain.NetworkManager

sealed class SwitchServerReason : java.io.Serializable {

    data class Downgrade(val fromTier: String, val toTier: String) : SwitchServerReason()
    object TrialEnded : SwitchServerReason()
    object UserBecameDelinquent : SwitchServerReason()
    object ServerInMaintenance : SwitchServerReason()
    object ServerUnreachable : SwitchServerReason()
    object ServerUnavailable : SwitchServerReason()
    object UnknownAuthFailure : SwitchServerReason()
}

sealed class VpnFallbackResult : java.io.Serializable {

    sealed class Switch() : VpnFallbackResult() {

        // null means change should be transparent for the user (no notification)
        abstract val log: String
        abstract val notificationReason: SwitchServerReason?
        abstract val fromProfile: Profile
        abstract val toProfile: Profile

        data class SwitchProfile(
            override val fromProfile: Profile,
            override val toProfile: Profile,
            override val notificationReason: SwitchServerReason? = null,
        ) : Switch() {
            override val log get() = "SwitchProfile ${toProfile.name} reason=$notificationReason"
        }

        data class SwitchServer(
            override val fromProfile: Profile,
            override val toProfile: Profile,
            val preparedConnection: PrepareResult,
            override val notificationReason: SwitchServerReason?,
            val compatibleProtocol: Boolean,
            val switchedSecureCore: Boolean,
        ) : Switch() {
            override val log get() = "SwitchServer ${preparedConnection.connectionParams.info} " +
                "reason=$notificationReason compatibleProtocol=$compatibleProtocol"
        }
    }

    data class Error(val type: ErrorType) : VpnFallbackResult()
}

data class PhysicalServer(val server: Server, val connectingDomain: ConnectingDomain)

class VpnConnectionErrorHandler(
    scope: CoroutineScope,
    private val appContext: Context,
    private val api: ProtonApiRetroFit,
    private val appConfig: AppConfig,
    private val userData: UserData,
    private val userPlanManager: UserPlanManager,
    private val serverManager: ServerManager,
    private val stateMonitor: VpnStateMonitor,
    private val serverListUpdater: ServerListUpdater,
    private val errorUIManager: VpnErrorUIManager,
    private val networkManager: NetworkManager,
    private val vpnBackendProvider: VpnBackendProvider,
) {
    private var handlingAuthError = false

    val switchConnectionFlow = MutableSharedFlow<VpnFallbackResult.Switch>()

    init {
        scope.launch {
            userPlanManager.infoChangeFlow.collect { changes ->
                if (!handlingAuthError && stateMonitor.isEstablishingOrConnected) {
                    getCommonFallbackForInfoChanges(stateMonitor.connectionProfile!!, changes)?.let {
                        switchConnectionFlow.emit(it)
                    }
                }
            }
        }
    }

    @SuppressWarnings("ReturnCount")
    private fun getCommonFallbackForInfoChanges(
        currentProfile: Profile,
        changes: List<UserPlanManager.InfoChange>
    ): VpnFallbackResult.Switch? {
        for (change in changes) when (change) {
            PlanChange.TrialEnded ->
                return VpnFallbackResult.Switch.SwitchProfile(
                    currentProfile,
                    serverManager.defaultFallbackConnection,
                    SwitchServerReason.TrialEnded
                )
            is PlanChange.Downgrade -> {
                return VpnFallbackResult.Switch.SwitchProfile(
                    currentProfile,
                    serverManager.defaultFallbackConnection,
                    SwitchServerReason.Downgrade(change.fromPlan, change.toPlan)
                )
            }
            UserBecameDelinquent ->
                return VpnFallbackResult.Switch.SwitchProfile(
                    currentProfile,
                    serverManager.defaultFallbackConnection,
                    SwitchServerReason.UserBecameDelinquent
                )
            else -> {}
        }
        return null
    }

    enum class CompatibilityAspect {
        Features, Tier, City, Country, SecureCore
    }

    private val smartReconnectEnabled get() =
        appConfig.getFeatureFlags().smartReconnect && userData.isSmartReconnectEnabled

    suspend fun onServerNotAvailable(profile: Profile) =
        fallbackToCompatibleServer(profile, null, SwitchServerReason.ServerUnavailable)

    suspend fun onServerInMaintenance(profile: Profile) =
        fallbackToCompatibleServer(profile, null, SwitchServerReason.ServerInMaintenance)

    suspend fun onUnreachableError(connectionParams: ConnectionParams): VpnFallbackResult =
        fallbackToCompatibleServer(connectionParams.profile, connectionParams, SwitchServerReason.ServerUnreachable)
            ?: VpnFallbackResult.Error(ErrorType.UNREACHABLE)

    private suspend fun fallbackToCompatibleServer(
        orgProfile: Profile,
        orgParams: ConnectionParams?,
        reason: SwitchServerReason
    ): VpnFallbackResult.Switch? {
        if (!smartReconnectEnabled) {
            ProtonLogger.log("Smart Reconnect disabled")
            return null
        }

        if (!networkManager.isConnectedToNetwork()) {
            ProtonLogger.log("No internet: aborting fallback")
            return null
        }

        val orgPhysicalServer = orgParams?.connectingDomain?.let { PhysicalServer(orgParams.server, it) }
        val candidates = getCandidateServers(orgProfile, orgPhysicalServer)

        candidates.forEach {
            ProtonLogger.log("Fallback server: ${it.connectingDomain.entryDomain} city=${it.server.city}")
        }

        val pingResult = vpnBackendProvider.pingAll(candidates, orgPhysicalServer) ?: run {
            ProtonLogger.log("No server responded")
            return null
        }

        // Original server + protocol responded, don't switch
        if (
            pingResult.physicalServer == orgPhysicalServer &&
            pingResult.responses.any { it.connectionParams.hasSameProtocolParams(orgParams) }
        ) {
            ProtonLogger.log("Got response for current connection - don't switch VPN server")
            return null
        }

        val expectedProtocolConnection = pingResult.getExpectedProtocolConnection(orgProfile)
        val score = getServerScore(pingResult.physicalServer.server, orgProfile)
        val secureCoreExpected = orgProfile.isSecureCore || userData.isSecureCoreEnabled
        val switchedSecureCore = secureCoreExpected && !hasCompatibility(score, CompatibilityAspect.SecureCore)
        val isCompatible = isCompatibleServer(score, pingResult.physicalServer, orgPhysicalServer) &&
            expectedProtocolConnection != null && !switchedSecureCore

        return VpnFallbackResult.Switch.SwitchServer(
            orgProfile,
            pingResult.profile,
            expectedProtocolConnection ?: pingResult.responses.first(),
            if (isCompatible) null else reason,
            compatibleProtocol = expectedProtocolConnection != null,
            switchedSecureCore = switchedSecureCore
        )
    }

    private fun hasCompatibility(score: Int, aspect: CompatibilityAspect) =
        score and (1 shl aspect.ordinal) != 0

    private fun isCompatibleServer(score: Int, physicalServer: PhysicalServer, orgPhysicalServer: PhysicalServer?) =
        hasCompatibility(score, CompatibilityAspect.Country) &&
        hasCompatibility(score, CompatibilityAspect.Features) &&
        hasCompatibility(score, CompatibilityAspect.SecureCore) &&
        (hasCompatibility(score, CompatibilityAspect.Tier) ||
            orgPhysicalServer == null || physicalServer.server.tier >= orgPhysicalServer.server.tier)

    // Return first response that's has compatible protocol with [profile] or null
    private fun VpnBackendProvider.PingResult.getExpectedProtocolConnection(profile: Profile): PrepareResult? {
        val expectedProtocol = profile.getProtocol(userData)
        if (expectedProtocol == VpnProtocol.Smart)
            return responses.first()

        return responses.firstOrNull {
            it.connectionParams.protocol == expectedProtocol && (it.connectionParams.transmission == null ||
                it.connectionParams.transmission == profile.getTransmissionProtocol(userData))
        }
    }

    private fun getCandidateServers(orgProfile: Profile, orgPhysicalServer: PhysicalServer?): List<PhysicalServer> {
        val candidateList = mutableListOf<PhysicalServer>()
        if (orgPhysicalServer != null)
            candidateList += orgPhysicalServer

        val secureCoreExpected = orgProfile.isSecureCore || userData.isSecureCoreEnabled
        val onlineServers = serverManager.getOnlineServers(secureCoreExpected)
        val scoredServers = sortServersByScore(onlineServers, orgProfile).run {
            if (orgPhysicalServer != null) {
                // Only include servers that have IP that differ from current connection.
                filter {
                    it.onlineConnectingDomains.size > 1 ||
                        it.onlineConnectingDomains.firstOrNull()?.entryIp != orgPhysicalServer.connectingDomain.entryIp
                }
            } else
                this
        }

        scoredServers.take(FALLBACK_SERVERS_COUNT - candidateList.size).map { server ->
            candidateList += PhysicalServer(
                server,
                server.onlineConnectingDomains.filter {
                    // Ignore connecting domains with the same IP as current connection.
                    it.entryIp != orgPhysicalServer?.connectingDomain?.entryIp
                }.random(),
            )
        }

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
        if (secureCoreExpected) {
            sortServersByScore(serverManager.getOnlineServers(false), orgProfile)
                .firstOrNull()?.let { fallbacks += it }
        }

        return candidateList.take(FALLBACK_SERVERS_COUNT - fallbacks.size) + fallbacks.map { server ->
            PhysicalServer(server, server.getRandomConnectingDomain())
        }
    }

    private fun getServerScore(
        server: Server,
        orgProfile: Profile,
    ): Int {
        var score = 0

        if (orgProfile.country.isBlank() || orgProfile.country == server.exitCountry)
            score += 1 shl CompatibilityAspect.Country.ordinal

        if (orgProfile.city.isNullOrBlank() || orgProfile.city == server.city)
            score += 1 shl CompatibilityAspect.City.ordinal

        if (userData.userTier == server.tier)
            // Prefer servers from user tier
            score += 1 shl CompatibilityAspect.Tier.ordinal

        if (orgProfile.directServer == null || server.features == orgProfile.directServer?.features)
            score += 1 shl CompatibilityAspect.Features.ordinal

        val secureCoreExpected = orgProfile.isSecureCore || userData.isSecureCoreEnabled
        if (!secureCoreExpected || server.isSecureCoreServer)
            score += 1 shl CompatibilityAspect.SecureCore.ordinal

        return score
    }

    private fun sortServersByScore(
        servers: List<Server>,
        profile: Profile,
    ) = sortByScore(servers) { getServerScore(it, profile) }

    private fun <T> sortByScore(servers: List<T>, scoreFun: (T) -> Int) =
        servers.map { Pair(it, scoreFun(it)) }.sortedByDescending { it.second }.map { it.first }

    @SuppressWarnings("ReturnCount")
    suspend fun onAuthError(connectionParams: ConnectionParams): VpnFallbackResult {
        try {
            handlingAuthError = true
            userPlanManager.refreshVpnInfo()?.let { infoChanges ->
                getCommonFallbackForInfoChanges(connectionParams.profile, infoChanges)?.let {
                    return it
                }

                if (VpnCredentials in infoChanges)
                    // Now that credentials are refreshed we can try reconnecting.
                    return VpnFallbackResult.Switch.SwitchProfile(connectionParams.profile, connectionParams.profile)

                val vpnInfo = requireNotNull(userData.vpnInfoResponse)
                val sessionCount = api.getSession().valueOrNull?.sessionList?.size ?: 0
                if (vpnInfo.maxSessionCount <= sessionCount)
                    return VpnFallbackResult.Error(ErrorType.MAX_SESSIONS)
            }

            return getMaintenanceFallback(connectionParams)
                // We couldn't establish if server is in maintenance, attempt searching for fallback anyway. Include
                // current connection in the search.
                ?: fallbackToCompatibleServer(
                    connectionParams.profile, connectionParams, SwitchServerReason.UnknownAuthFailure)
                ?: VpnFallbackResult.Error(ErrorType.AUTH_FAILED)
        } finally {
            handlingAuthError = false
        }
    }

    private suspend fun getMaintenanceFallback(connectionParams: ConnectionParams): VpnFallbackResult.Switch? {
        if (!appConfig.isMaintenanceTrackerEnabled())
            return null

        ProtonLogger.log("Checking if server is not in maintenance")
        val domainId = connectionParams.connectingDomain?.id ?: return null
        val result = api.getConnectingDomain(domainId)
        if (result is ApiResult.Success) {
            val connectingDomain = result.value.connectingDomain
            if (!connectingDomain.isOnline) {
                ProtonLogger.log("Current server is in maintenance (${connectingDomain.entryDomain})")
                serverManager.updateServerDomainStatus(connectingDomain)
                serverListUpdater.updateServerList()
                val sentryEvent = EventBuilder()
                    .withMessage("Maintenance detected")
                    .withExtra("Server", result.value.connectingDomain.entryDomain)
                    .build()
                ProtonLogger.logSentryEvent(sentryEvent)
                return if (smartReconnectEnabled) {
                    onServerInMaintenance(connectionParams.profile)
                    fallbackToCompatibleServer(
                        connectionParams.profile, null, SwitchServerReason.ServerInMaintenance
                    )
                }
                else
                    VpnFallbackResult.Switch.SwitchProfile(
                        connectionParams.profile,
                        serverManager.defaultFallbackConnection
                    )
            }
        }
        return null
    }

    suspend fun maintenanceCheck() {
        stateMonitor.connectionParams?.let { params ->
            getMaintenanceFallback(params)?.let {
                switchConnectionFlow.emit(it)
            }
        }
    }

    companion object {
        private const val FALLBACK_SERVERS_COUNT = 5
    }
}
