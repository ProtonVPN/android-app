/*
 * Copyright (c) 2021 Proton Technologies AG
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

package com.protonvpn.android.utils

import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultCallback
import androidx.lifecycle.lifecycleScope
import com.protonvpn.android.ProtonApplication
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import me.proton.core.humanverification.presentation.entity.HumanVerificationInput
import me.proton.core.humanverification.presentation.entity.HumanVerificationResult
import me.proton.core.humanverification.presentation.ui.StartHumanVerification
import me.proton.core.network.domain.session.SessionId
import me.proton.core.network.domain.session.SessionListener

class HumanVerificationHandler(
    val scope: CoroutineScope,
    val app: ProtonApplication,
) {

    sealed class Result(val sessionId: SessionId) {
        class Failure(sessionId: SessionId) : Result(sessionId)
        class Success(sessionId: SessionId, val tokenType: String, val tokenCode: String) : Result(sessionId)

        fun toHumanVerificationResult() = when(this) {
            is Failure -> SessionListener.HumanVerificationResult.Failure
            is Success -> SessionListener.HumanVerificationResult.Success
        }
    }

    private val resultFlow = MutableSharedFlow<Result>()

    val currentHandlerName get() = (app.foregroundActivity as? ComponentActivity)?.javaClass?.simpleName

    private val activityResultCallback = ActivityResultCallback<HumanVerificationResult?> { result ->
        result?.let {
            scope.launch {
                if (!it.tokenType.isNullOrBlank() && !it.tokenCode.isNullOrBlank()) {
                    resultFlow.emit(Result.Success(
                        sessionId = SessionId(it.sessionId),
                        tokenType = it.tokenType!!,
                        tokenCode = it.tokenCode!!
                    ))
                } else {
                    resultFlow.emit(Result.Failure(
                        sessionId = SessionId(it.sessionId)))
                }
            }
        }
    }

    suspend fun verify(input: HumanVerificationInput): Result {
        (app.foregroundActivity as? ComponentActivity)?.let { activity ->
            val launcher = activity.activityResultRegistry.register(
                "HumanVerification", StartHumanVerification(), activityResultCallback)
            launcher.launch(input)
            return supervisorScope {
                try {
                    withContext(activity.lifecycleScope.coroutineContext) {
                        resultFlow.first {
                            it.sessionId.id == input.sessionId
                        }
                    }
                } catch (e: CancellationException) {
                    Result.Failure(SessionId(input.sessionId))
                }
            }
        }
        return Result.Failure(SessionId(input.sessionId))
    }
}
