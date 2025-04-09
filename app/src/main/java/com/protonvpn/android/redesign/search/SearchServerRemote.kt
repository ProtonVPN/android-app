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

package com.protonvpn.android.redesign.search

import com.protonvpn.android.api.ProtonApiRetroFit
import com.protonvpn.android.models.vpn.Server
import com.protonvpn.android.models.vpn.ServerSearchResponse
import com.protonvpn.android.redesign.search.ui.addServerNameHash
import com.protonvpn.android.servers.ServerManager2
import com.protonvpn.android.vpn.usecases.ServerListTruncationEnabled
import com.protonvpn.android.vpn.usecases.TransientMustHaves
import dagger.Reusable
import kotlinx.coroutines.flow.first
import me.proton.core.network.domain.ApiResult
import me.proton.core.network.domain.HttpResponseCodes
import javax.inject.Inject

sealed interface FetchServerResult {
    data class Success(val server: Server) : FetchServerResult
    object TryLater : FetchServerResult
    object None : FetchServerResult
}

@Reusable
class FetchServerByName @Inject constructor(
    private val api: ProtonApiRetroFit,
) {
    suspend operator fun invoke(serverName: String): FetchServerResult {
        val result = api.getServerByName(serverName)
        return when {
            result is ApiResult.Success<ServerSearchResponse> ->
                result.value.logicalServer?.let { FetchServerResult.Success(it) } ?: FetchServerResult.None
            result is ApiResult.Error.Http && result.httpCode == HttpResponseCodes.HTTP_TOO_MANY_REQUESTS ->
                FetchServerResult.TryLater
            else -> FetchServerResult.None
        }
    }
}

@Reusable
class SearchServerRemote @Inject constructor(
    private val isEnabled: ServerListTruncationEnabled,
    private val fetchServerByName: FetchServerByName,
    private val transientMustHaves: TransientMustHaves,
    private val serverManager: ServerManager2,
) {

    suspend operator fun invoke(query: String): FetchServerResult {
        if (isEnabled() && !isValidForRemoteSearch(query))
            return FetchServerResult.None

        val serverTerm = addServerNameHash(query).uppercase()
        val availableLocally = serverManager.allServersFlow.first().any { server ->
            server.serverName == serverTerm
        }
        return if (!availableLocally) {
            val result = fetchServerByName(serverTerm)
            if (result is FetchServerResult.Success) {
                transientMustHaves.add(result.server.serverId)
                serverManager.updateOrAddServer(result.server)
            }
            result
        } else {
            FetchServerResult.None
        }
    }

    private fun isValidForRemoteSearch(query: String): Boolean =
        query.isNotBlank() && REMOTE_QUERY_REGEX.matches(query)

    companion object {
        private val REMOTE_QUERY_REGEX = Regex("""^[a-zA-Z]{2,}(-[a-zA-Z]{2,})*(#)?[0-9]+(-[a-zA-Z0-9]{2,})*$""")
    }
}
