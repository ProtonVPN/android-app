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

package com.protonvpn.android.tv.login

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.protonvpn.android.logging.LogCategory
import com.protonvpn.android.logging.LogLevel
import com.protonvpn.android.logging.ProtonLogger
import com.protonvpn.android.utils.Constants
import dagger.hilt.android.lifecycle.HiltViewModel
import io.sentry.Sentry
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withTimeoutOrNull
import me.proton.core.account.domain.entity.AccountType
import me.proton.core.auth.domain.entity.EncryptedAuthSecret
import me.proton.core.auth.domain.entity.SessionForkSelector
import me.proton.core.auth.domain.entity.SessionForkUserCode
import me.proton.core.auth.domain.repository.AuthRepository
import me.proton.core.auth.domain.usecase.CreateLoginSessionFromFork
import me.proton.core.auth.domain.usecase.PostLoginAccountSetup
import me.proton.core.devicemigration.domain.usecase.PullEdmSessionFork
import me.proton.core.devicemigration.presentation.qr.QrBitmapGenerator
import me.proton.core.network.domain.ApiException
import me.proton.core.network.domain.session.Session
import javax.inject.Inject
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

private val QrCodeTimeout = 10.minutes
private const val QrCodeUrlPrefix = "https://account.proton.me/vpn/tv/code"

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TvQrLoginViewModel @Inject constructor(
    private val qrBitmapGenerator: QrBitmapGenerator,
    private val authRepository: AuthRepository,
    private val accountType: AccountType,
    private val pullEdmSessionFork: PullEdmSessionFork,
    private val createLoginSessionFromFork: CreateLoginSessionFromFork,
    private val postLoginAccountSetup: PostLoginAccountSetup,
) : ViewModel() {

    sealed interface ViewState {

        interface WithCode {
            val userCode: String
            val bitmap: Bitmap
        }

        object Loading : ViewState
        class ForkReady(override val userCode: String, override val bitmap: Bitmap) : ViewState, WithCode
        object ForkFailed : ViewState

        sealed interface PollingFailed : ViewState {
            object Error : PollingFailed
            object Timeout : PollingFailed
        }

        sealed interface Login : ViewState {
            // Include fork data in the success status to keep displaying it while the UI navigates
            // to the main activity. It looks better than switching to a spinner for half a second.
            class Success(override val userCode: String, override val bitmap: Bitmap) : Login, WithCode
            object Error : Login
        }
    }

    data class ForkData(
        val selector: SessionForkSelector,
        val userCode: SessionForkUserCode,
        val qrCode: Bitmap,
    )

    private val triggerNewQrCode = Channel<Unit>(capacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    private val sessionForkFlow = flow {
        val fork = initiateFork()
        if (fork != null) {
            emit(ViewState.ForkReady(fork.userCode.value, fork.qrCode))
            val pollingResult = pollForSessionFork(fork.selector)
            when (pollingResult) {
                is PollResult.Success -> {
                    emit(login(pollingResult.session, fork))
                }

                PollResult.Error -> emit(ViewState.PollingFailed.Error)
                PollResult.Timeout -> emit(ViewState.PollingFailed.Timeout)
            }
        } else {
            emit(ViewState.ForkFailed)
        }
    }

    val viewState: StateFlow<ViewState> = triggerNewQrCode.receiveAsFlow()
        .flatMapLatest { sessionForkFlow }
        .onStart { triggerNewQrCode.trySend(Unit) }
        .catch { e -> logUnexpectedState("TV sign in unexpected error", e) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ViewState.Loading)

    fun createNewCode() {
        triggerNewQrCode.trySend(Unit)
    }

    private suspend fun initiateFork(): ForkData? {
        try {
            val (selector, userCode) = authRepository.getSessionForks(sessionId = null)
            val url = Uri.parse(QrCodeUrlPrefix).buildUpon()
                .appendPath(userCode.value)
                .appendQueryParameter("clientId", Constants.TV_CLIENT_ID)
                .build()
            val qrCode = qrBitmapGenerator(url.toString(), 200.dp)
            return ForkData(selector, userCode, qrCode)
        } catch (e: ApiException) {
            ProtonLogger.logCustom(
                LogLevel.WARN,
                LogCategory.USER,
                "TV sign in: unable to initiate session fork ${e.error}"
            )
            return null
        }
    }

    private sealed interface PollResult {
        data class Success(val session: Session.Authenticated) : PollResult
        object Error : PollResult
        object Timeout : PollResult
    }

    private suspend fun pollForSessionFork(selector: SessionForkSelector): PollResult =
        withTimeoutOrNull(QrCodeTimeout) {
            pullEdmSessionFork(encryptionKey = null, selector = selector, pollDuration = 5.seconds)
                .mapNotNull { result ->
                    when (result) {
                        PullEdmSessionFork.Result.Awaiting,
                        PullEdmSessionFork.Result.Loading -> null

                        PullEdmSessionFork.Result.NoConnection -> {
                            ProtonLogger.logCustom(
                                LogLevel.WARN,
                                LogCategory.USER,
                                "No connection while polling for fork finished."
                            )
                            PollResult.Error
                        }

                        is PullEdmSessionFork.Result.Success ->
                            PollResult.Success(result.session)

                        is PullEdmSessionFork.Result.UnrecoverableError -> {
                            ProtonLogger.logCustom(
                                LogLevel.ERROR,
                                LogCategory.USER,
                                "Tv sign in: error while polling for fork finished: ${result.cause}"
                            )
                            PollResult.Error
                        }
                    }
                }
                .catch { e ->
                    ProtonLogger.logCustom(
                        LogLevel.ERROR,
                        LogCategory.USER,
                        "TV sign in: exception while polling for fork finished: $e"
                    )
                    emit(PollResult.Error)
                }
                .last()
        } ?: PollResult.Timeout

    private suspend fun login(session: Session.Authenticated, fork: ForkData): ViewState.Login {
        try {
            createLoginSessionFromFork(accountType, null, session)
        } catch (e: ApiException) {
            ProtonLogger.logCustom(LogCategory.USER, "TV sign in failed: creating session failed: $e")
            return ViewState.Login.Error
        }

        val result = postLoginAccountSetup(
            userId = session.userId,
            encryptedAuthSecret = EncryptedAuthSecret.Absent,
            requiredAccountType = accountType,
            isSecondFactorNeeded = false,
            isTwoPassModeNeeded = false,
            temporaryPassword = false,
        )
        return when (result) {
            is PostLoginAccountSetup.Result.AccountReady -> {
                ProtonLogger.logCustom(LogCategory.USER, "TV sign in successful")
                ViewState.Login.Success(fork.userCode.value, fork.qrCode)
            }
            is PostLoginAccountSetup.Result.Error.UserCheckError -> {
                ProtonLogger.logCustom(LogCategory.USER, "TV sign in failed: user check: ${result.error}")
                ViewState.Login.Error
            }

            is PostLoginAccountSetup.Result.Error.UnlockPrimaryKeyError,
            is PostLoginAccountSetup.Result.Need.ChangePassword,
            is PostLoginAccountSetup.Result.Need.ChooseUsername,
            is PostLoginAccountSetup.Result.Need.DeviceSecret,
            is PostLoginAccountSetup.Result.Need.SecondFactor,
            is PostLoginAccountSetup.Result.Need.TwoPassMode -> {
                logUnexpectedState("TV sign in failed: unexpected state $result")
                ViewState.Login.Error
            }
        }
    }

    private fun logUnexpectedState(message: String, e: Throwable? = null) {
        ProtonLogger.logCustom(LogLevel.ERROR, LogCategory.USER, message)
        val error = e ?: IllegalStateException(message)
        Sentry.captureException(error)
    }
}