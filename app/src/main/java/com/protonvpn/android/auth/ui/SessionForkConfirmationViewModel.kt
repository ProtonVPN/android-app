/*
 * Copyright (c) 2026. Proton AG
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

package com.protonvpn.android.auth.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.auth.usecase.IsQrCodeTvLoginFeatureFlagEnabled
import com.protonvpn.android.logging.LogCategory
import com.protonvpn.android.logging.ProtonLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import me.proton.core.auth.domain.usecase.ForkSession
import me.proton.core.network.domain.ApiException
import me.proton.core.network.domain.ApiResult
import me.proton.core.util.kotlin.equalsNoCase
import me.proton.core.util.kotlin.startsWith
import javax.inject.Inject

@HiltViewModel
class SessionForkConfirmationViewModel @Inject constructor(
    private val mainScope: CoroutineScope,
    private val currentUser: CurrentUser,
    private val forkSession: ForkSession,
    private val isQrCodeTvLoginFeatureFlagEnabled: IsQrCodeTvLoginFeatureFlagEnabled
) : ViewModel() {

    sealed interface ViewState {
        object Initializing : ViewState
        data class AskForkConfirmation(val isLoading: Boolean) : ViewState
        object ErrorUserCodeInvalid : ViewState
        object ErrorBusinessUser : ViewState
        object ForkSuccess : ViewState
        sealed interface ForkError : ViewState {
            object Expired : ForkError
            object Network : ForkError
            object Fatal : ForkError
        }
        object Finished : ViewState
    }

    private var userCode: String? = null

    val viewState = MutableStateFlow<ViewState>(ViewState.Initializing)

    fun start(uri: Uri?) {
        viewModelScope.launch {
            if (isQrCodeTvLoginFeatureFlagEnabled()) {
                // TODO: handle B2B users
                // TODO: log in if necessary.
                viewState.value = confirmationStep(uri)
            } else {
                // No regular user should be scanning QR codes when the FF is disabled.
                viewState.value = ViewState.Finished
            }
        }
    }

    fun confirmFork() {
        requireNotNull(userCode)
        viewState.value = ViewState.AskForkConfirmation(isLoading = true)
        mainScope.launch {
            val userId = currentUser.user()?.userId
            if (userId != null) {
                try {
                    forkSession(
                        userId = userId,
                        payload = null,
                        childClientId = "android_tv-vpn",
                        independent = true,
                        userCode = userCode
                    )
                    viewState.value = ViewState.ForkSuccess
                } catch (e: ApiException) {
                    ProtonLogger.logCustom(LogCategory.USER, "Error when confirming session fork: ${e.error}")
                    val error = e.error
                    val errorState: ViewState.ForkError = when (error) {
                        is ApiResult.Error.Connection -> ViewState.ForkError.Network
                        is ApiResult.Error.Parse -> ViewState.ForkError.Fatal
                        is ApiResult.Error.Http -> when {
                            error.proton?.code == 2501 -> ViewState.ForkError.Expired
                            else -> ViewState.ForkError.Fatal
                        }
                    }
                    viewState.value = errorState
                }
            } else {
                viewState.value = ViewState.ForkError.Fatal
            }
        }
    }

    private fun confirmationStep(uri: Uri?): ViewState {
        this.userCode = extractUserCode(uri)
        return if (userCode != null) {
            ViewState.AskForkConfirmation(isLoading = false)
        } else {
            ViewState.ErrorUserCodeInvalid
        }
    }

    private fun extractUserCode(uri: Uri?): String? {
        if (uri == null) return null

        val pathPrefix = "/vpn/tv/code/"
        return if ("https".equalsNoCase(uri.scheme) &&
            "account.proton.me".equalsNoCase(uri.host) &&
            uri.path?.startsWith(pathPrefix) == true
        ) {
            val userCode = uri.path?.substring(pathPrefix.length)
            // Only very basic validation here, the backend will reject invalid codes.
            return userCode?.takeIf { it.length == 8 }
        } else {
            null
        }
    }
}