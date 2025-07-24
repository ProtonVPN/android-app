/*
 * Copyright (c) 2025. Proton AG
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

package com.protonvpn.android.servers

import com.protonvpn.android.api.ProtonApiRetroFit
import com.protonvpn.android.appconfig.periodicupdates.PeriodicActionResult
import com.protonvpn.android.appconfig.periodicupdates.toPeriodicActionResultWithCustomValue
import com.protonvpn.android.auth.data.VpnUser
import com.protonvpn.android.di.WallClock
import com.protonvpn.android.logging.ApiLogResponse
import com.protonvpn.android.logging.ProtonLogger
import com.protonvpn.android.servers.api.LogicalsResponse
import com.protonvpn.android.servers.api.LogicalsStatusId
import com.protonvpn.android.servers.api.ServerListV1
import com.protonvpn.android.ui.home.ServerListUpdaterPrefs
import com.protonvpn.android.ui.home.updateTier
import com.protonvpn.android.utils.CountryTools
import com.protonvpn.android.utils.DebugUtils
import com.protonvpn.android.utils.ServerManager
import com.protonvpn.android.vpn.ProtocolSelection
import com.protonvpn.android.vpn.usecases.GetTruncationMustHaveIDs
import com.protonvpn.android.vpn.usecases.ServerListTruncationEnabled
import dagger.Reusable
import kotlinx.coroutines.withContext
import me.proton.core.network.domain.ApiResult
import me.proton.core.util.kotlin.DispatcherProvider
import retrofit2.Response
import java.util.Date
import javax.inject.Inject

@Reusable
class UpdateServerListFromApi @Inject constructor(
    private val api: ProtonApiRetroFit,
    private val dispatcherProvider: DispatcherProvider,
    @WallClock private val wallClock: () -> Long,
    private val serverManager: ServerManager,
    private val prefs: ServerListUpdaterPrefs,
    private val updateWithBinaryStatus: UpdateServersWithBinaryStatus,
    private val binaryServerStatusEnabled: IsBinaryServerStatusEnabled,
    private val truncationFeatureFlagEnabled: ServerListTruncationEnabled,
    private val getTruncationMustHaveIDs: GetTruncationMustHaveIDs,
) {
    sealed interface Result {
        object Success : Result
        data class Error(val apiError: ApiResult.Error?) : Result
    }

    private sealed interface FetchResult {
        data class NewServers(
            val newServers: List<Server>,
            val statusId: LogicalsStatusId? = null,
            val isListTruncated: Boolean? = null,
            val lastModified: Date? = null,
        ): FetchResult

        object NotModified : FetchResult

        // This should not happen.
        object EmptyBody : FetchResult

        object BinaryStatusError : FetchResult

        data class ApiError(val apiError: ApiResult.Error) : FetchResult
    }

    suspend operator fun invoke(
        netzone: String?,
        lang: String,
        freeOnly: Boolean,
        serverListLastModified: Long
    ): PeriodicActionResult<Result> {
        val realProtocolsNames = ProtocolSelection.REAL_PROTOCOLS.map { it.apiName }
        val enableTruncation = truncationFeatureFlagEnabled()
        val requestedMustHaveIDs = if (enableTruncation) getTruncationMustHaveIDs() else emptySet()
        val fetchResult = if (binaryServerStatusEnabled()) {
            DebugUtils.debugAssert("Partial updates are not supported with binary status") { !freeOnly }
            val listResult = api.getServerList(
                netzone,
                lang = lang,
                protocols = realProtocolsNames,
                lastModified = serverListLastModified,
                enableTruncation = enableTruncation,
                mustHaveIDs = requestedMustHaveIDs,
            )
            processServerListResult(listResult,  ::processServerList)
        } else {
            val listResult = api.getServerListV1(
                netzone,
                lang,
                realProtocolsNames,
                freeOnly,
                enableTruncation = enableTruncation,
                lastModified = serverListLastModified,
                mustHaveIDs = requestedMustHaveIDs,
            )
            processServerListResult(listResult, ::processV1ServerList)
        }

        if (fetchResult is FetchResult.NewServers) {
            serverManager.ensureLoaded()
            val retainIDs = if (fetchResult.isListTruncated == true) {
                // retain only those ID that were not in must-haves for this call
                getTruncationMustHaveIDs() - requestedMustHaveIDs
            } else {
                emptySet()
            }

            val newList = if (freeOnly) {
                serverManager.allServers.updateTier(fetchResult.newServers, VpnUser.FREE_TIER, retainIDs)
            } else {
                fetchResult.newServers
            }
            if (fetchResult.lastModified != null)
                prefs.serverListLastModified = fetchResult.lastModified.time

            debugCountryCheck(newList)

            serverManager.setServers(newList,  statusId = fetchResult.statusId,lang, retainIDs = retainIDs)
            serverManager.updateTimestamp()
            if (!freeOnly)
                prefs.lastFullUpdateTimestamp = wallClock()
        }

        return when(fetchResult) {
            is FetchResult.ApiError ->
                fetchResult.apiError.toPeriodicActionResultWithCustomValue(
                    Result.Error(fetchResult.apiError),
                    isSuccess = false,
                )

            is FetchResult.EmptyBody,
            is FetchResult.BinaryStatusError ->
                PeriodicActionResult(Result.Error(null), isSuccess = false)

            is FetchResult.NewServers,
            is FetchResult.NotModified ->
                PeriodicActionResult(Result.Success, isSuccess = true)
        }
    }

    private suspend fun <T> processServerListResult(
        result: ApiResult<Response<T>>,
        processNewServers: suspend (body: T, lastModifier: Date?) -> FetchResult
    ): FetchResult = when(result) {
        is ApiResult.Error.Http ->
            if (result.httpCode == HTTP_NOT_MODIFIED_304) {
                FetchResult.NotModified
            } else {
                FetchResult.ApiError(result)
            }
        is ApiResult.Error ->
            FetchResult.ApiError(result)
        is ApiResult.Success -> with(result.value) {
            val body = body()
            when {
                result.value.isSuccessful && body != null ->
                    processNewServers(body, headers().getDate("Last-Modified"))
                result.value.isSuccessful ->
                    FetchResult.EmptyBody
                else -> {
                    val request = raw().request
                    ProtonLogger.log(
                        ApiLogResponse,
                        "HTTP ${code()} ${message()} ${request.method} ${request.url} (took ${durationMs()}ms)"
                    )
                    if (code() == HTTP_NOT_MODIFIED_304) {
                        FetchResult.NotModified
                    } else {
                        FetchResult.ApiError(ApiResult.Error.Http(code(), message()))
                    }
                }
            }
        }
    }

    private fun processV1ServerList(
        body: ServerListV1,
        lastModified: Date?,
    ): FetchResult.NewServers = FetchResult.NewServers(
        newServers = body.serverList.toServers(),
        statusId = null,
        isListTruncated = body.metadata?.listIsTruncated,
        lastModified = lastModified,
    )

    private suspend fun processServerList(
        body: LogicalsResponse,
        lastModified: Date?,
    ): FetchResult {
        val statusId = body.statusId
        val statusDataResult = api.getBinaryStatus(statusId)
        return when(statusDataResult) {
            is ApiResult.Success -> {
                val partialServers = body.serverList.toPartialServers()

                val servers = withContext(dispatcherProvider.Comp) {
                    updateWithBinaryStatus(partialServers, statusDataResult.value)
                }
                if (servers != null) {
                    FetchResult.NewServers(
                        newServers = servers,
                        statusId = statusId,
                        isListTruncated = body.metadata?.listIsTruncated,
                        lastModified = lastModified,
                    )
                } else {
                    FetchResult.BinaryStatusError
                }
            }

            is ApiResult.Error -> FetchResult.ApiError(statusDataResult)
        }
    }

    private fun debugCountryCheck(serverList: List<Server>) {
        DebugUtils.debugAssert("Country with no continent") {
            val countriesWithNoContinent = serverList
                .flatMapTo(HashSet()) { listOf(it.entryCountry, it.exitCountry) }
                .filter { CountryTools.oldMapLocations[it]?.continent == null }
            countriesWithNoContinent.isEmpty()
        }
    }

    private fun Response<*>.durationMs() = with(raw()) { receivedResponseAtMillis - sentRequestAtMillis }

    companion object {
        private const val HTTP_NOT_MODIFIED_304 = 304
    }
}
