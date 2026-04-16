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

import android.util.Base64
import com.protonvpn.android.logging.LogCategory
import com.protonvpn.android.logging.ProtonLogger
import com.protonvpn.android.utils.runCatchingCheckedExceptions
import dagger.Reusable
import kotlinx.coroutines.delay
import me.proton.core.auth.domain.entity.SessionForkSelector
import me.proton.core.auth.domain.repository.AuthRepository
import me.proton.core.devicemigration.domain.entity.EncryptionKey
import me.proton.core.network.domain.ApiException
import me.proton.core.network.domain.ApiResult
import me.proton.core.network.domain.HttpResponseCodes.HTTP_UNPROCESSABLE
import me.proton.core.network.domain.isHttpError
import me.proton.core.network.domain.session.Session
import javax.inject.Inject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@Reusable
class PollSessionFork @Inject constructor(
    private val authRepository: AuthRepository,
) {
    data class Result(val session: Session.Authenticated, val payload: String?)

    @Throws
    suspend operator fun invoke(
        encryptionKey: EncryptionKey?,
        selector: SessionForkSelector,
        pollDuration: Duration = 5.seconds,
    ): Result {
        var result: Result? = null
        while (result == null) {
            suspend {
                delay(pollDuration)
                val (payloadBase64, session) = authRepository.getForkedSession(selector)
                result = Result(session, payloadBase64?.let { decodePayload(it) })
            }.runCatchingCheckedExceptions { e ->
                when {
                    e !is ApiException -> throw e
                    e.error is ApiResult.Error.Connection -> Unit // retry
                    e.isHttpError(HTTP_UNPROCESSABLE) -> Unit // retry
                    else -> throw e
                }
            }
        }
        return result
    }
}

private fun decodePayload(payloadBase64: String): String? =
    try {
        Base64.decode(payloadBase64, Base64.NO_WRAP).decodeToString()
    } catch (e : IllegalArgumentException) {
        ProtonLogger.logCustom(LogCategory.USER, "Error decoding session fork payload: $e")
        null
    }
