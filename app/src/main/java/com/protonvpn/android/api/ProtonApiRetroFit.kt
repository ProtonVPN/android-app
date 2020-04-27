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

import com.protonvpn.android.BuildConfig
import com.protonvpn.android.appconfig.AppConfigResponse
import com.protonvpn.android.components.LoaderUI
import com.protonvpn.android.models.login.GenericResponse
import com.protonvpn.android.models.login.LoginBody
import com.protonvpn.android.models.login.LoginInfoBody
import com.protonvpn.android.models.login.SessionListResponse
import com.protonvpn.android.models.login.VpnInfoResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import okhttp3.RequestBody
import org.jetbrains.annotations.TestOnly
import retrofit2.Response

//TODO: remove dependencies on activity/network loaders, refactor callbacks to suspending functions
open class ProtonApiRetroFit(val scope: CoroutineScope, private val manager: ProtonApiManager) {

    suspend fun getAppConfig(): ApiResult<AppConfigResponse> =
        manager.call(useBackoff = true) { it.getAppConfig() }

    suspend fun getLocation() =
        manager.call { it.getLocation() }

    fun postBugReport(
        loader: LoaderUI,
        params: RequestBody,
        callback: NetworkResultCallback<GenericResponse>
    ) = makeCall(callback, loader) { it.postBugReport(params) }

    open suspend fun getServerList(loader: LoaderUI?, ip: String?) =
        makeCall(loader, useBackoff = true) { it.getServers(ip) }

    suspend fun postLogin(body: LoginBody) =
        manager.call(true) { it.postLogin(body) }

    suspend fun postLoginInfo(email: String) =
        manager.call(useBackoff = true) { it.postLoginInfo(LoginInfoBody(email)) }

    open suspend fun getVPNInfo() =
        manager.call(useBackoff = true) { it.getVPNInfo() }

    open fun getVPNInfo(loader: LoaderUI, callback: NetworkResultCallback<VpnInfoResponse>) =
        makeCall(callback, loader) { it.getVPNInfo() }

    open fun logout(callback: NetworkResultCallback<GenericResponse>) =
        makeCall(callback) { it.postLogout() }

    open suspend fun getSession(): ApiResult<SessionListResponse> =
        manager.call(useBackoff = true) { it.getSession() }

    private suspend fun <T> makeCall(
        loader: LoaderUI?,
        useBackoff: Boolean,
        callFun: suspend (ProtonVPNRetrofit) -> Response<T>
    ): ApiResult<T> {
        loader?.switchToLoading()
        val result = manager.call(useBackoff, callFun)
        when (result) {
            is ApiResult.Success -> {
                loader?.switchToEmpty()
            }
            is ApiResult.Error -> {
                if (result is ApiResult.Failure && BuildConfig.DEBUG)
                    result.exception.printStackTrace()
                loader?.switchToRetry(result)
            }
        }
        return result
    }

    private fun <T> makeCall(
        callback: NetworkResultCallback<T>,
        loader: LoaderUI? = null,
        callFun: suspend (ProtonVPNRetrofit) -> Response<T>
    ) = scope.launch {
        loader?.switchToLoading()
        when (val result = manager.call(true, callFun)) {
            is ApiResult.Success -> {
                loader?.switchToEmpty()
                callback.onSuccess(result.value)
            }
            is ApiResult.Error -> {
                if (result is ApiResult.Failure && BuildConfig.DEBUG)
                    result.exception.printStackTrace()
                loader?.switchToRetry(result)
                callback.onFailure()
            }
        }
    }

    @TestOnly
    fun getOkClient() = manager.primaryOkClient
}
