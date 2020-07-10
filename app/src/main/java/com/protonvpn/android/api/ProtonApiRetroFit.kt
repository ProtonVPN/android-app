/*
 * Copyright (c) 2020 Proton Technologies AG
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
package com.protonvpn.android.api

import com.protonvpn.android.appconfig.AppConfigResponse
import com.protonvpn.android.components.LoaderUI
import com.protonvpn.android.models.login.GenericResponse
import com.protonvpn.android.models.login.LoginBody
import com.protonvpn.android.models.login.LoginInfoBody
import com.protonvpn.android.models.login.SessionListResponse
import com.protonvpn.android.models.login.VpnInfoResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import me.proton.core.network.domain.ApiManager
import me.proton.core.network.domain.ApiResult
import okhttp3.RequestBody

//TODO: remove dependencies on activity/network loaders, refactor callbacks to suspending functions
open class ProtonApiRetroFit(val scope: CoroutineScope, private val manager: ApiManager<ProtonVPNRetrofit>) {

    suspend fun getAppConfig(): ApiResult<AppConfigResponse> =
        manager { getAppConfig() }

    suspend fun getLocation() =
        manager { getLocation() }

    fun postBugReport(
        loader: LoaderUI,
        params: RequestBody,
        callback: NetworkResultCallback<GenericResponse>
    ) = makeCall(callback, loader) { it.postBugReport(params) }

    open suspend fun getServerList(loader: LoaderUI?, ip: String?) =
        makeCall(loader) { it.getServers(ip) }

    open suspend fun getLoads(ip: String?) =
        manager { getLoads(ip) }

    suspend fun postLogin(body: LoginBody) =
        manager { postLogin(body) }

    suspend fun postLoginInfo(email: String) =
        manager { postLoginInfo(LoginInfoBody(email)) }

    open suspend fun getVPNInfo() =
        manager { getVPNInfo() }

    open fun getVPNInfo(callback: NetworkResultCallback<VpnInfoResponse>) =
        makeCall(callback) { it.getVPNInfo() }

    open fun logout(callback: NetworkResultCallback<GenericResponse>) =
        makeCall(callback) { it.postLogout() }

    open suspend fun getSession(): ApiResult<SessionListResponse> =
        manager { getSession() }

    private suspend fun <T> makeCall(
        loader: LoaderUI?,
        callFun: suspend (ProtonVPNRetrofit) -> T
    ): ApiResult<T> {
        loader?.switchToLoading()
        val result = manager(block = callFun)
        when (result) {
            is ApiResult.Success -> {
                loader?.switchToEmpty()
            }
            is ApiResult.Error -> {
                loader?.switchToRetry(result)
            }
        }
        return result
    }

    private fun <T> makeCall(
        callback: NetworkResultCallback<T>,
        loader: LoaderUI? = null,
        callFun: suspend (ProtonVPNRetrofit) -> T
    ) = scope.launch {
        loader?.switchToLoading()
        when (val result = manager(block = callFun)) {
            is ApiResult.Success -> {
                loader?.switchToEmpty()
                callback.onSuccess(result.value)
            }
            is ApiResult.Error -> {
                loader?.switchToRetry(result)
                callback.onFailure()
            }
        }
    }
}
