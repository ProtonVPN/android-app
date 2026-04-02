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

package com.protonvpn.android.auth.ui.sessionfork

import android.content.Intent
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.protonvpn.android.R
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.di.ElapsedRealtimeClock
import com.protonvpn.android.logging.LogCategory
import com.protonvpn.android.logging.ProtonLogger
import com.protonvpn.android.redesign.app.ui.CreateLaunchIntent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import me.proton.core.auth.domain.usecase.ForkSession
import me.proton.core.domain.entity.UserId
import me.proton.core.network.domain.ApiException
import me.proton.core.network.domain.ApiResult
import me.proton.core.presentation.savedstate.state
import me.proton.core.user.domain.entity.Type
import me.proton.core.util.kotlin.equalsNoCase
import me.proton.core.util.kotlin.startsWith
import javax.inject.Inject

private const val SUCCESS_DELAY_MS = 4_000L
private const val MIN_TIME_FOR_READING_MS = 3_000L

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SessionForkConfirmationViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val currentUser: CurrentUser,
    private val forkSession: ForkSession,
    private val createLaunchIntent: CreateLaunchIntent,
    @param:ElapsedRealtimeClock private val clock: () -> Long,
) : ViewModel() {

    sealed interface ViewState {
        object Initial : ViewState
        data class AskForkConfirmation(val isLoading: Boolean) : ViewState
        object ErrorUserCodeInvalid : ViewState
        object ErrorBusinessUser : ViewState
        sealed interface Fork : ViewState {
            data class Success(val startActivityOnDone: Intent?): Fork
            sealed interface Error : Fork {
                object Expired : Error
                object Network : Error
                object Fatal : Error
            }
        }
    }

    private var qrCodeUri: Uri? = null
    private var hasSignIn by savedStateHandle.state(false)
    private var hasTriggeredUpgrade by savedStateHandle.state(false)
    private val triggerConfirmSignIn = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private var lastResumedTimestampMs = Long.MIN_VALUE

    // Use a channel to guarantee event delivery even if there is no observer at the moment the
    // event is generated.
    val eventLaunchUpgrade =
        Channel<Unit>(capacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val eventShowToast = MutableSharedFlow<Int>(extraBufferCapacity = 1)

    val viewState =
        flow {
            val jointUser = currentUser.jointUserFlow.filterNotNull().first {
                it.user.type != Type.CredentialLess
            }
            val vpnUser = jointUser.vpnUser
            if (vpnUser.isBusiness) {
                emit(ViewState.ErrorBusinessUser)
            } else {
                if (vpnUser.isFreeUser && hasSignIn && !hasTriggeredUpgrade) {
                    // Store "hasTriggeredUpgrade" in case the activity is recreated after upgrade
                    // is dismissed.
                    hasTriggeredUpgrade = true
                    eventLaunchUpgrade.send(Unit)
                }
                val userCode = extractUserCode(qrCodeUri)
                if (userCode != null) {
                    emit(ViewState.AskForkConfirmation(isLoading = false))
                    triggerConfirmSignIn.collect {
                        emit(ViewState.AskForkConfirmation(isLoading = true))
                        emitAll(executeConfirmFork(vpnUser.userId, userCode))
                    }
                } else {
                    emit(ViewState.ErrorUserCodeInvalid)
                }
            }
        }.stateIn(viewModelScope, SharingStarted.Lazily, ViewState.Initial)

    fun initialize(qrCodeUri: Uri?) {
        this.qrCodeUri = qrCodeUri
    }

    fun onSignInRequired() {
        hasSignIn = true
    }

    fun onResume() {
        lastResumedTimestampMs = clock()
    }

    fun confirmFork() {
        if (lastResumedTimestampMs + MIN_TIME_FOR_READING_MS >= clock()) {
            eventShowToast.tryEmit(R.string.session_fork_confirmation_too_soon_toast)
        } else {
            triggerConfirmSignIn.tryEmit(Unit)
        }
    }

    // Uses channelFlow to allow embedding it in viewState flow.
    private fun executeConfirmFork(userId: UserId, userCode: String): Flow<ViewState.Fork> = channelFlow {
        withContext(NonCancellable) {
            try {
                forkSession(
                    userId = userId,
                    payload = null,
                    childClientId = "android_tv-vpn",
                    independent = true,
                    userCode = userCode
                )
                val mainUiLaunchIntent = if (hasSignIn) {
                    createLaunchIntent.withFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    )
                } else {
                    null
                }
                // It takes a few seconds for the TV to check for success.
                // The user might be confused if they get a success screen on their mobile while
                // nothing happens on the TV, so let's keep the spinner spinning for a little while.
                delay(SUCCESS_DELAY_MS)
                send(ViewState.Fork.Success(mainUiLaunchIntent))
            } catch (e: ApiException) {
                ProtonLogger.logCustom(
                    LogCategory.USER,
                    "Error when confirming session fork: ${e.error}"
                )
                val errorState: ViewState.Fork.Error = when (val error = e.error) {
                    is ApiResult.Error.Connection -> ViewState.Fork.Error.Network
                    is ApiResult.Error.Parse -> ViewState.Fork.Error.Fatal
                    is ApiResult.Error.Http -> when {
                        error.proton?.code == 2501 -> ViewState.Fork.Error.Expired
                        else -> ViewState.Fork.Error.Fatal
                    }
                }
                send(errorState)
            }
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