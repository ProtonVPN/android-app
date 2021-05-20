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

package com.protonvpn.android.api

import android.content.Context
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.contract.ActivityResultContract
import androidx.lifecycle.lifecycleScope
import com.protonvpn.android.ProtonApplication
import com.protonvpn.android.utils.ProtonLogger
import com.protonvpn.android.utils.Storage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import me.proton.core.humanverification.domain.HumanVerificationWorkflowHandler
import me.proton.core.humanverification.domain.repository.HumanVerificationRepository
import me.proton.core.humanverification.presentation.entity.HumanVerificationInput
import me.proton.core.humanverification.presentation.entity.HumanVerificationResult
import me.proton.core.humanverification.presentation.ui.HumanVerificationActivity
import me.proton.core.network.domain.client.ClientId
import me.proton.core.network.domain.client.getType
import me.proton.core.network.domain.humanverification.HumanVerificationAvailableMethods
import me.proton.core.network.domain.humanverification.HumanVerificationDetails
import me.proton.core.network.domain.humanverification.HumanVerificationListener
import me.proton.core.network.domain.humanverification.HumanVerificationProvider
import me.proton.core.network.domain.humanverification.HumanVerificationState

class HumanVerificationHandler(
    val scope: CoroutineScope,
    val app: ProtonApplication,
) : HumanVerificationListener, HumanVerificationProvider,
    HumanVerificationWorkflowHandler, HumanVerificationRepository {

    sealed class Result(val clientId: ClientId) {
        class Failure(clientId: ClientId) : Result(clientId)
        class Success(clientId: ClientId, val tokenType: String, val tokenCode: String) : Result(clientId)

        fun toHumanVerificationResult() = when (this) {
            is Failure -> HumanVerificationListener.HumanVerificationResult.Failure
            is Success -> HumanVerificationListener.HumanVerificationResult.Success
        }
    }

    class StartHumanVerification : ActivityResultContract<HumanVerificationInput, HumanVerificationResult?>() {

        override fun createIntent(context: Context, input: HumanVerificationInput) =
            Intent(context, HumanVerificationActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(HumanVerificationActivity.ARG_HUMAN_VERIFICATION_INPUT, input)
            }

        override fun parseResult(resultCode: Int, result: Intent?): HumanVerificationResult? =
            result?.getParcelableExtra(HumanVerificationActivity.ARG_HUMAN_VERIFICATION_RESULT)
    }

    private class HumanVerificationDetailsData(val details: MutableMap<ClientId, HumanVerificationDetails>)

    private val resultFlow = MutableSharedFlow<Result>()

    // Needed so that each activity result callback have unique key
    private var keyCounter = 0

    private val humanVerificationDetailsData by lazy {
        Storage.load<HumanVerificationDetailsData>(HumanVerificationDetailsData::class.java) {
            HumanVerificationDetailsData(mutableMapOf())
        }
    }

    private fun setDetails(clientId: ClientId, details: HumanVerificationDetails) {
        humanVerificationDetailsData.details[clientId] = details
        Storage.save(humanVerificationDetailsData)
    }

    fun clear() {
        humanVerificationDetailsData.details.clear()
        Storage.delete(HumanVerificationDetailsData::class.java)
    }

    val currentHandlerName get() = (app.foregroundActivity as? ComponentActivity)?.javaClass?.simpleName

    private val activityResultCallback = ActivityResultCallback<HumanVerificationResult?> {
        // result handled by handleHumanVerification* functions
    }

    suspend fun verify(clientId: ClientId, input: HumanVerificationInput): Result {
        (app.foregroundActivity as? ComponentActivity)?.let { activity ->
            val launcher = activity.activityResultRegistry.register(
                "HumanVerification${keyCounter++}", StartHumanVerification(), activityResultCallback)
            launcher.launch(input)
            return supervisorScope {
                try {
                    withContext(activity.lifecycleScope.coroutineContext) {
                        resultFlow.first {
                            it.clientId == clientId
                        }
                    }
                } catch (e: CancellationException) {
                    Result.Failure(clientId)
                }
            }
        }
        return Result.Failure(clientId)
    }

    override suspend fun onHumanVerificationNeeded(
        clientId: ClientId,
        methods: HumanVerificationAvailableMethods
    ): HumanVerificationListener.HumanVerificationResult {
        ProtonLogger.log("Human verification needed: handler=$currentHandlerName")

        setDetails(clientId, HumanVerificationDetails(clientId, methods.verificationMethods,
            methods.captchaVerificationToken, HumanVerificationState.HumanVerificationNeeded))

        val handlerResult = verify(clientId, HumanVerificationInput(
            clientId = clientId.id,
            clientIdType = clientId.getType().value,
            verificationMethods = methods.verificationMethods.map { it.value },
            captchaToken = methods.captchaVerificationToken
        ))

        return handlerResult.toHumanVerificationResult()
    }

    override suspend fun getHumanVerificationDetails(clientId: ClientId) =
        humanVerificationDetailsData.details[clientId]

    override suspend fun insertHumanVerificationDetails(details: HumanVerificationDetails) {
        setDetails(details.clientId, details)
    }

    override suspend fun onHumanVerificationInvalid(clientId: ClientId) {
        humanVerificationDetailsData.details[clientId]?.let {
            setDetails(clientId, it.copy(state = HumanVerificationState.HumanVerificationInvalid))
        }
    }

    // We don't need to implement these
    override fun onHumanVerificationStateChanged(initialState: Boolean): Flow<HumanVerificationDetails> = emptyFlow()
    override suspend fun getAllHumanVerificationDetails(): Flow<List<HumanVerificationDetails>> = emptyFlow()

    override suspend fun updateHumanVerificationState(
        clientId: ClientId,
        state: HumanVerificationState,
        tokenType: String?,
        tokenCode: String?
    ) {
        humanVerificationDetailsData.details[clientId]?.let {
            setDetails(clientId, it.copy(state = state, tokenCode = tokenCode, tokenType = tokenType))
        }
    }

    override suspend fun handleHumanVerificationFailed(clientId: ClientId) {
        ProtonLogger.log("human verification failed")
        resultFlow.emit(Result.Failure(clientId))
    }

    override suspend fun handleHumanVerificationSuccess(clientId: ClientId, tokenType: String, tokenCode: String) {
        ProtonLogger.log("human verification success")
        humanVerificationDetailsData.details[clientId]?.let {
            setDetails(clientId, it.copy(
                state = HumanVerificationState.HumanVerificationSuccess,
                tokenType = tokenType,
                tokenCode = tokenCode,
            ))
        }
        resultFlow.emit(Result.Success(
            clientId = clientId,
            tokenType = tokenType,
            tokenCode = tokenCode
        ))
    }
}
