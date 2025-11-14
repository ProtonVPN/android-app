/*
 * Copyright (c) 2024 Proton AG
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

package com.protonvpn.android.managed.usecase

import com.protonvpn.android.logging.LogCategory
import com.protonvpn.android.logging.ProtonLogger
import com.protonvpn.android.managed.AutoLoginConfig
import com.protonvpn.android.utils.runCatchingCheckedExceptions
import me.proton.core.account.domain.entity.AccountType
import me.proton.core.auth.domain.usecase.CreateLoginSession
import me.proton.core.auth.domain.usecase.PostLoginAccountSetup
import me.proton.core.crypto.common.keystore.KeyStoreCrypto
import me.proton.core.crypto.common.keystore.encrypt
import me.proton.core.domain.entity.UserId
import javax.inject.Inject

interface AutoLogin {
    suspend fun execute(config: AutoLoginConfig): Result<UserId>
}

class AutoLoginException(message: String? = null) : Exception(message)

class AutoLoginImpl @Inject constructor(
    private val createLoginSession: CreateLoginSession,
    private val postLoginAccountSetup: PostLoginAccountSetup,
    private val keyStoreCrypto: KeyStoreCrypto,
) : AutoLogin {

    override suspend fun execute(config: AutoLoginConfig): Result<UserId> = suspend {
        val encryptedPassword = config.password.encrypt(keyStoreCrypto)
        val accountType = AccountType.Username
        val sessionInfo =
            createLoginSession(config.username, encryptedPassword, accountType)
        val result = postLoginAccountSetup(
            userId = sessionInfo.userId,
            encryptedPassword = encryptedPassword,
            requiredAccountType = accountType,
            isSecondFactorNeeded = sessionInfo.isSecondFactorNeeded,
            isTwoPassModeNeeded = sessionInfo.isTwoPassModeNeeded,
            temporaryPassword = sessionInfo.temporaryPassword
        )
        when (result) {
            is PostLoginAccountSetup.Result.AccountReady ->
                Result.success(sessionInfo.userId)
            is PostLoginAccountSetup.Result.Error.UserCheckError ->
                Result.failure(AutoLoginException(result.error.localizedMessage))
            else -> {
                ProtonLogger.logCustom(
                    LogCategory.MANAGED_CONFIG,
                    "Unexpected post login result: $result",
                )
                Result.failure(AutoLoginException())
            }
        }
    }.runCatchingCheckedExceptions {
        Result.failure(it)
    }
}
