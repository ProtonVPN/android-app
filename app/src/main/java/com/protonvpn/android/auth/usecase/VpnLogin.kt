/*
 * Copyright (c) 2021 Proton AG
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

package com.protonvpn.android.auth.usecase

import android.content.Context
import com.protonvpn.android.appconfig.AppConfig
import com.protonvpn.android.appconfig.CachedPurchaseEnabled
import com.protonvpn.android.vpn.CertificateRepository
import dagger.Reusable
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import me.proton.core.network.domain.ApiResult
import me.proton.core.network.domain.session.SessionId
import me.proton.core.network.domain.session.SessionProvider
import me.proton.core.user.domain.entity.User
import javax.inject.Inject
import me.proton.core.auth.presentation.R as AuthR

@Reusable
class VpnLogin @Inject constructor(
    private val sessionProvider: SessionProvider,
    private val certificateRepository: CertificateRepository,
    private val purchaseEnabled: CachedPurchaseEnabled,
    private val appConfig: AppConfig,
) {
    sealed class Result {
        object Success : Result()
        class Error(val message: String) : Result()
    }

    suspend operator fun invoke(user: User, context: Context): Result = coroutineScope {
        purchaseEnabled.forceRefresh()
        val sessionId = sessionProvider.getSessionId(user.userId)
        requireNotNull(sessionId)
        val appConfigDeferred = async { appConfig.forceUpdate(user.userId) }
        val certificateDeferred = async { fetchCertificate(sessionId) }

        val appConfigResult = appConfigDeferred.await()
        val certificateFetched = certificateDeferred.await()
        if (certificateFetched && appConfigResult.isSuccess) {
            Result.Success
        } else {
            val appConfigError = (appConfigResult as? ApiResult.Error.Http)?.proton?.error
            Result.Error(appConfigError ?: context.getString(AuthR.string.auth_login_general_error))
        }
    }

    private suspend fun fetchCertificate(sessionId: SessionId): Boolean {
        certificateRepository.generateNewKey(sessionId)
        val certificateResult = certificateRepository.updateCertificate(sessionId, false)
        return certificateResult is CertificateRepository.CertificateResult.Success
    }
}
