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
import com.protonvpn.android.logging.ApiLogResponse
import com.protonvpn.android.logging.ProtonLogger
import com.protonvpn.android.servers.api.LogicalsResponse
import com.protonvpn.android.servers.api.LogicalsStatusId
import com.protonvpn.android.servers.api.ServerListV1
import com.protonvpn.android.utils.DebugUtils
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
class FetchServerListWithStatus @Inject constructor(
    private val api: ProtonApiRetroFit,
    private val dispatcherProvider: DispatcherProvider,
    private val updateWithBinaryStatus: UpdateServersWithBinaryStatus,
    private val binaryServerStatusEnabled: IsBinaryServerStatusEnabled,
    private val truncationFeatureFlagEnabled: ServerListTruncationEnabled,
    private val getTruncationMustHaveIDs: GetTruncationMustHaveIDs,
) {
    sealed interface FetchResult {
        data class NewServers(
            val newServers: List<Server>,
            val statusId: LogicalsStatusId? = null,
            val isListTruncated: Boolean? = null,
            val freeOnly: Boolean,
            val lastModified: Date? = null,
            val usedMustHaveIDs: Set<String>,
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
    ): FetchResult {
        val realProtocolsNames = ProtocolSelection.REAL_PROTOCOLS.map { it.apiName }
        val enableTruncation = truncationFeatureFlagEnabled()
        val mustHaveIDs = if (enableTruncation) getTruncationMustHaveIDs() else emptySet()
        return if (binaryServerStatusEnabled()) {
            DebugUtils.debugAssert("Partial updates are not supported with binary status") { !freeOnly }
            val listResult = api.getServerList(
                netzone,
                lang = lang,
                protocols = realProtocolsNames,
                lastModified = serverListLastModified,
                enableTruncation = enableTruncation,
                mustHaveIDs = mustHaveIDs,
            )
            processServerListResult(listResult, freeOnly, mustHaveIDs, ::processServerList)
        } else {
            val listResult = api.getServerListV1(
                netzone,
                lang,
                realProtocolsNames,
                freeOnly,
                enableTruncation = enableTruncation,
                lastModified = serverListLastModified,
                mustHaveIDs = mustHaveIDs,
            )
            processServerListResult(listResult, freeOnly, mustHaveIDs, ::processV1ServerList)
        }
    }

    private suspend fun <T> processServerListResult(
        result: ApiResult<Response<T>>,
        freeOnly: Boolean,
        usedMustHaveIDs: Set<String>,
        processNewServers: suspend (body: T, lastModifier: Date?, freeOnly: Boolean, usedMustHaveIDs: Set<String>) -> FetchResult
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
                    processNewServers(body, headers().getDate("Last-Modified"), freeOnly, usedMustHaveIDs)
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
        freeOnly: Boolean,
        usedMustHaveIDs: Set<String>
    ): FetchResult.NewServers = FetchResult.NewServers(
        newServers = body.serverList.toServers(),
        statusId = null,
        isListTruncated = body.metadata?.listIsTruncated,
        freeOnly = freeOnly,
        lastModified = lastModified,
        usedMustHaveIDs = usedMustHaveIDs,
    )

    private suspend fun processServerList(
        body: LogicalsResponse,
        lastModified: Date?,
        freeOnly: Boolean,
        usedMustHaveIDs: Set<String>
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
                        freeOnly = freeOnly,
                        lastModified = lastModified,
                        usedMustHaveIDs = usedMustHaveIDs,
                    )
                } else {
                    FetchResult.BinaryStatusError
                }
            }

            is ApiResult.Error -> FetchResult.ApiError(statusDataResult)
        }
    }

    private fun Response<*>.durationMs() = with(raw()) { receivedResponseAtMillis - sentRequestAtMillis }

    companion object {
        private const val HTTP_NOT_MODIFIED_304 = 304
    }
}
