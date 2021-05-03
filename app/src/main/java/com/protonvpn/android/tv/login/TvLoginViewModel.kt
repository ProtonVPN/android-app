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

package com.protonvpn.android.tv.login

import android.os.SystemClock
import androidx.annotation.StringRes
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.protonvpn.android.R
import com.protonvpn.android.api.ProtonApiRetroFit
import com.protonvpn.android.appconfig.AppConfig
import com.protonvpn.android.appconfig.ForkedSessionResponse
import com.protonvpn.android.appconfig.SessionForkSelectorResponse
import com.protonvpn.android.models.config.UserData
import com.protonvpn.android.tv.login.TvLoginViewState.Companion.toLoginError
import com.protonvpn.android.ui.home.ServerListUpdater
import com.protonvpn.android.utils.ServerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import me.proton.core.network.domain.ApiResult
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class TvLoginViewModel @Inject constructor(
    val userData: UserData,
    val appConfig: AppConfig,
    val api: ProtonApiRetroFit,
    val serverListUpdater: ServerListUpdater,
    val serverManager: ServerManager
) : ViewModel() {

    val state = MutableLiveData<TvLoginViewState>()

    fun onEnterScreen(scope: CoroutineScope) {
        if (userData.isLoggedIn) {
            if (serverManager.isDownloadedAtLeastOnce)
                state.value = TvLoginViewState.Success
            else scope.launch {
                loadInitialConfig()
            }
        } else {
            state.value = TvLoginViewState.Welcome
        }
    }

    fun startLogin(scope: CoroutineScope) {
        scope.launch {
            state.value = TvLoginViewState.FetchingCode
            when (val selectorResult = api.getSessionForkSelector()) {
                is ApiResult.Success -> {
                    if (selectorResult.value.userCode.length != CODE_LENGTH)
                        state.value = TvLoginViewState.Error(R.string.loaderErrorGeneric, R.string.try_again)
                    else
                        pollLogin(selectorResult.value)
                }
                is ApiResult.Error -> {
                    state.value = selectorResult.toLoginError()
                }
            }
        }
    }

    private suspend fun pollLogin(code: SessionForkSelectorResponse) {
        val result: ApiResult<ForkedSessionResponse>? = withTimeoutOrNull(POLL_TIMEOUT_MS) {
            api.getAvailableDomains()
            val pollStart = monoClockMs()
            val updateTimer = launch {
                while (true) {
                    state.value = TvLoginViewState.PollingSession(code.userCode, TimeUnit.MILLISECONDS.toSeconds(
                        pollStart + POLL_TIMEOUT_MS - monoClockMs()))
                    delay(1000)
                }
            }
            repeatWithTimeoutOrNull(POLL_TIMEOUT_MS) {
                delay(POLL_DELAY_MS)
                pollSession(code.selector)
            }.also {
                updateTimer.cancel()
            }
        }

        when (result) {
            null -> {
                state.value = TvLoginViewState.Error(
                    R.string.tv_login_title_refresh, R.string.tv_login_refresh)
            }
            is ApiResult.Error -> {
                state.value = result.toLoginError()
            }
            is ApiResult.Success -> {
                // We don't have access token yet as forked session don't return it, use
                // invalid access token so it's refreshed by the core network module.
                userData.setLoginResponse(result.value.toLoginResponse("invalid"))
                when (val infoResult = api.getVPNInfo()) {
                    is ApiResult.Error -> {
                        userData.clearNetworkUserData()
                        state.value = infoResult.toLoginError()
                    }
                    is ApiResult.Success -> {
                        userData.setLoggedIn(infoResult.value)
                        loadInitialConfig()
                    }
                }
            }
        }
    }

    private suspend fun loadInitialConfig() {
        state.value = TvLoginViewState.Loading
        appConfig.update()
        serverListUpdater.updateLocation()
        when (val result = serverListUpdater.updateServerList()) {
            is ApiResult.Success ->
                state.value = TvLoginViewState.Success
            is ApiResult.Error ->
                state.value = result.toLoginError()
        }
    }

    private suspend fun pollSession(selector: String) =
        api.getForkedSession(selector).takeIf {
            !(it is ApiResult.Error.Http && it.httpCode == HTTP_CODE_KEEP_POLLING)
        }

    private suspend fun <T : Any> repeatWithTimeoutOrNull(timeoutMs: Long, block: suspend () -> T?): T? =
        withTimeoutOrNull(timeoutMs) {
            var current: T?
            do {
                current = block()
            } while (current == null)
            current
        }

    private fun monoClockMs() = SystemClock.elapsedRealtime()

    companion object {
        val POLL_TIMEOUT_MS = TimeUnit.MINUTES.toMillis(5)
        val POLL_DELAY_MS = TimeUnit.SECONDS.toMillis(5)
        const val HTTP_CODE_KEEP_POLLING = 422
        const val CODE_LENGTH = 8
    }
}

sealed class TvLoginViewState(
    val title: String? = null,
    @StringRes val titleRes: Int = 0,
    @StringRes val buttonLabelRes: Int = 0,
    @StringRes val descriptionRes: Int = 0
) {
    object Welcome : TvLoginViewState(
        titleRes = R.string.tv_login_title_welcome,
        buttonLabelRes = R.string.tv_login_welcome_button,
        descriptionRes = R.string.tv_login_welcome_description)

    object Success : TvLoginViewState()
    object FetchingCode : TvLoginViewState(titleRes = R.string.tv_login_title_loading)
    object Loading : TvLoginViewState()
    class PollingSession(val code: String, val secondsLeft: Long) :
        TvLoginViewState(titleRes = R.string.tv_login_title_signin)
    class Error(
        @StringRes errorTitleRes: Int,
        @StringRes errorButtonLabelRes: Int,
        errorTitle: String? = null
    ) : TvLoginViewState(errorTitle, errorTitleRes, errorButtonLabelRes)

    companion object {

        fun ApiResult.Error.toLoginError(): Error = when (this) {
            is ApiResult.Error.NoInternet ->
                Error(R.string.loaderErrorNoInternet, R.string.try_again)
            is ApiResult.Error.Http -> {
                if (proton?.error != null)
                    Error(0, R.string.try_again, proton?.error)
                else
                    Error(R.string.loaderErrorGeneric, R.string.try_again)
            }
            is ApiResult.Error.Timeout ->
                Error(R.string.loaderErrorTimeout, R.string.try_again)
            else ->
                Error(R.string.loaderErrorGeneric, R.string.try_again)
        }
    }
}
