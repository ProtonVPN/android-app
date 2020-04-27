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

import androidx.lifecycle.ViewModel
import com.protonvpn.android.api.ApiResult
import com.protonvpn.android.api.ProtonApiRetroFit
import com.protonvpn.android.models.config.UserData
import com.protonvpn.android.models.login.LoginBody
import com.protonvpn.android.models.login.LoginInfoResponse
import com.protonvpn.android.models.login.LoginResponse
import com.protonvpn.android.utils.ConstantTime
import com.protonvpn.android.utils.Storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import srp.Auth
import srp.Proofs
import javax.inject.Inject

class LoginViewModel @Inject constructor(val userData: UserData, val api: ProtonApiRetroFit) : ViewModel() {

    private suspend fun getProofs(
        username: String,
        password: String,
        infoResponse: LoginInfoResponse
    ): Proofs? = withContext(Dispatchers.Default) {
        val auth = Auth(
            infoResponse.version,
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
                Storage.save(loginResult.value)
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

    suspend fun login(password: String): LoginState {
        Storage.delete(LoginResponse::class.java)
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
