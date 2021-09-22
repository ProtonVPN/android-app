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
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.protonvpn.android.api.GuestHole
import com.protonvpn.android.api.ProtonApiRetroFit
import com.protonvpn.android.appconfig.AppConfig
import com.protonvpn.android.models.config.UserData
import com.protonvpn.android.models.login.LoginBody
import com.protonvpn.android.models.login.LoginInfoResponse
import com.protonvpn.android.models.login.VpnInfoResponse
import com.protonvpn.android.utils.ConstantTime
import com.protonvpn.android.utils.ServerManager
import com.protonvpn.android.vpn.CertificateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import me.proton.core.network.domain.ApiResult
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    val userData: UserData,
    val appConfig: AppConfig,
    val api: ProtonApiRetroFit,
    private val guestHole: GuestHole,
    val serverManager: ServerManager,
    private val proofsProvider: ProofsProvider,
    val certificateRepository: CertificateRepository,
) : ViewModel() {

    private val _loginState = MutableLiveData<LoginState>(LoginState.EnterCredentials)
    val loginState: LiveData<LoginState> get() { return _loginState }

    init {
        if (!userData.isLoggedIn) {
            viewModelScope.launch {
                api.getAvailableDomains()
            }
        }
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
                        certificateRepository.generateNewKey(loginResult.value.sessionId)
                        handleVpnInfoResult(infoResult.value)
                    }
                }
            }
        }
    }

    private suspend fun handleVpnInfoResult(vpnInfoResponse: VpnInfoResponse): LoginState {
        val vpnInfo = vpnInfoResponse.vpnInfo
        return when {
            vpnInfo.hasNoConnectionsAssigned -> {
                api.logout()
                LoginState.ConnectionAllocationPrompt
            }
            vpnInfo.userTierUnknown -> {
                api.logout()
                LoginState.Error(
                    ApiResult.Error.Connection(false, Exception("User tier unknown")),
                    false
                )
            }
            else -> {
                userData.setLoggedIn(vpnInfoResponse)
                LoginState.Success
            }
        }
    }

    fun onBackToLogin() {
        _loginState.value = LoginState.RetryLogin
    }

    fun onBackPressed(): Boolean {
        if (loginState.value is LoginState.Error ||
            loginState.value is LoginState.ConnectionAllocationPrompt) {
            _loginState.value = LoginState.EnterCredentials
            return true
        }
        return false
    }

    fun onVpnPrepareFailed() {
        _loginState.value = LoginState.Error(
            ApiResult.Error.Connection(false, Exception("Vpn permission not granted")),
            false)
    }

    suspend fun login(context: Context, user: String, password: ByteArray) {
        _loginState.postValue(LoginState.InProgress)
        var result = makeInfoResponseCall(user, password)
        if (result is LoginState.Error && result.error.isPotentialBlocking) {
            loginWithGuestHole(context, user, password)?.let { result = it }
        }
        _loginState.postValue(result)
        appConfig.update()
    }

    private suspend fun loginWithGuestHole(
        context: Context,
        user: String,
        password: ByteArray
    ): LoginState? {
        _loginState.postValue(LoginState.GuestHoleActivated)
        return guestHole.call(context) {
            appConfig.update()
            makeInfoResponseCall(user, password).apply {
                if (this is LoginState.Success && serverManager.isOutdated) {
                    val serversResult = api.getServerList(null, null)
                    if (serversResult is ApiResult.Success) serverManager.setServers(serversResult.value.serverList)
                }
            }
        }
    }

    private suspend fun makeInfoResponseCall(user: String, password: ByteArray): LoginState {
        userData.clearNetworkUserData()
        return when (val loginInfoResult = api.postLoginInfo(user)) {
            is ApiResult.Error -> LoginState.Error(loginInfoResult, true)
            is ApiResult.Success -> {
                val loginBody = getLoginBody(loginInfoResult.value, user, password)
                if (loginBody == null) {
                    LoginState.UnsupportedAuth
                } else {
                    loginWithProofs(loginBody)
                }
            }
        }
    }

    private suspend fun getLoginBody(loginInfo: LoginInfoResponse, user: String, password: ByteArray): LoginBody? {
        val proofs = proofsProvider.getProofs(user, password, loginInfo) ?: return null
        return LoginBody(
            user,
            loginInfo.srpSession,
            ConstantTime.encodeBase64(proofs.clientEphemeral, true),
            ConstantTime.encodeBase64(proofs.clientProof, true),
            ""
        )
    }
}
