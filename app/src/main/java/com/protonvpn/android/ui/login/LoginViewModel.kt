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
package com.protonvpn.android.ui.login

import android.content.Context
import android.content.Intent
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.protonvpn.android.api.GuestHole
import com.protonvpn.android.api.ProtonApiRetroFit
import com.protonvpn.android.appconfig.AppConfig
import com.protonvpn.android.models.config.UserData
import com.protonvpn.android.models.login.LoginBody
import com.protonvpn.android.models.login.LoginInfoResponse
import com.protonvpn.android.utils.ConstantTime
import com.protonvpn.android.utils.ServerManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.proton.core.network.domain.ApiResult
import srp.Auth
import srp.Proofs
import javax.inject.Inject

class LoginViewModel @Inject constructor(
    val userData: UserData,
    val appConfig: AppConfig,
    val api: ProtonApiRetroFit,
    private val guestHole: GuestHole,
    val serverManager: ServerManager
) : ViewModel() {

    val loginState = MutableLiveData<LoginState>()

    private suspend fun getProofs(
        username: String,
        password: String,
        infoResponse: LoginInfoResponse
    ): Proofs? = withContext(Dispatchers.Default) {
        val auth = Auth(
            infoResponse.getVersion(),
            username,
            password,
            infoResponse.salt,
            infoResponse.modulus,
            infoResponse.serverEphemeral
        )
        auth.generateProofs(2048)
    }

    private suspend fun loginWithProofs(loginBody: LoginBody): LoginState {
        return when (val loginResult = api.postLogin(loginBody)) {
            is ApiResult.Error -> {
                LoginState.Error(loginResult, false)
            }

            is ApiResult.Success -> {
                userData.setLoginResponse(loginResult.value)
                when (val infoResult = api.getVPNInfo()) {
                    is ApiResult.Error -> LoginState.Error(infoResult, true)
                    is ApiResult.Success -> {
                        userData.setLoggedIn(infoResult.valueOrNull)
                        LoginState.Success
                    }
                }
            }
        }
    }

    suspend fun login(context: Context, password: String, prepareIntentHandler: ((Intent) -> Unit)) {
        loginState.postValue(LoginState.InProgress)
        var result = makeInfoResponseCall(password)
        if (result is LoginState.Error && result.error.isPotentialBlocking) {
            loginWithGuestHole(context, password, prepareIntentHandler)?.let { result = it }
        }
        loginState.postValue(result)
        appConfig.update()
    }

    private suspend fun loginWithGuestHole(
        context: Context,
        password: String,
        prepareIntentHandler: ((Intent) -> Unit)
    ): LoginState? {
        loginState.postValue(LoginState.GuestHoleActivated)
        return guestHole.call(context, prepareIntentHandler) {
            appConfig.update()
            makeInfoResponseCall(password).apply {
                if (this is LoginState.Success && serverManager.isOutdated) {
                    val serversResult = api.getServerList(null, null)
                    if (serversResult is ApiResult.Success) serverManager.setServers(serversResult.value.serverList)
                }
            }
        }
    }

    private suspend fun makeInfoResponseCall(password: String): LoginState {
        userData.clearNetworkUserData()
        return when (val loginInfoResult = api.postLoginInfo(userData.user)) {
            is ApiResult.Error -> LoginState.Error(loginInfoResult, true)
            is ApiResult.Success -> {
                val loginBody = getLoginBody(loginInfoResult.value, password)
                if (loginBody == null) {
                    LoginState.UnsupportedAuth
                } else {
                    loginWithProofs(loginBody)
                }
            }
        }
    }

    private suspend fun getLoginBody(loginInfo: LoginInfoResponse, password: String): LoginBody? {
        val proofs = getProofs(userData.user, password, loginInfo) ?: return null
        return LoginBody(
            userData.user,
            loginInfo.srpSession,
            ConstantTime.encodeBase64(proofs.clientEphemeral, true),
            ConstantTime.encodeBase64(proofs.clientProof, true),
            ""
        )
    }
}
