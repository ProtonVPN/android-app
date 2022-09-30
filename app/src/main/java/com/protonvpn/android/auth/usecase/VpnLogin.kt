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

package com.protonvpn.android.auth.usecase

import android.content.Context
import com.protonvpn.android.R
import com.protonvpn.android.api.ProtonApiRetroFit
import com.protonvpn.android.appconfig.AppConfig
import com.protonvpn.android.appconfig.CachedPurchaseEnabled
import com.protonvpn.android.auth.data.VpnUser
import com.protonvpn.android.auth.data.VpnUserDao
import com.protonvpn.android.logging.ProtonLogger
import com.protonvpn.android.logging.UserPlanChanged
import com.protonvpn.android.logging.toLog
import com.protonvpn.android.models.login.toVpnUserEntity
import com.protonvpn.android.ui.home.ServerListUpdater
import com.protonvpn.android.ui.onboarding.OnboardingPreferences
import com.protonvpn.android.utils.Storage
import com.protonvpn.android.vpn.CertificateRepository
import kotlinx.coroutines.CoroutineScope
import me.proton.core.network.domain.ApiResult
import me.proton.core.network.domain.session.SessionProvider
import me.proton.core.user.domain.entity.User
import javax.inject.Inject

class VpnLogin @Inject constructor(
    val mainScope: CoroutineScope,
    val api: ProtonApiRetroFit,
    val sessionProvider: SessionProvider,
    val vpnUserDao: VpnUserDao,
    val certificateRepository: CertificateRepository,
    val currentUser: CurrentUser,
    val purchaseEnabled: CachedPurchaseEnabled,
    val appConfig: AppConfig,
    val serverListUpdater: ServerListUpdater
) {
    sealed class Result {
        class Success(val vpnUser: VpnUser) : Result()
        class Error(val message: String) : Result()
        object AssignConnections : Result()
    }

    suspend operator fun invoke(user: User, context: Context): Result {
        purchaseEnabled.refresh()
        val sessionId = sessionProvider.getSessionId(user.userId)
        requireNotNull(sessionId)
        return when (val vpnResult = api.getVPNInfo(sessionId)) {
            is ApiResult.Error.Http -> {
                if (vpnResult.proton?.code == ERROR_CODE_NO_CONNECTIONS_ASSIGNED) {
                    Result.AssignConnections
                } else {
                    Result.Error(vpnResult.proton?.error ?: context.getString(R.string.auth_login_general_error))
                }
            }
            is ApiResult.Error ->
                Result.Error(context.getString(R.string.auth_login_general_error))
            is ApiResult.Success -> {
                val vpnInfo = vpnResult.value.vpnInfo
                when {
                    vpnInfo.userTierUnknown ->
                        Result.Error(context.getString(R.string.auth_login_general_error))
                    vpnInfo.hasNoConnectionsAssigned ->
                        Result.AssignConnections
                    else -> {
                        certificateRepository.generateNewKey(sessionId)
                        val certificateFetched =
                            certificateRepository.updateCertificate(
                                sessionId,
                                false
                            ) is CertificateRepository.CertificateResult.Success
                        if (certificateFetched) {
                            val vpnUser = vpnResult.value.toVpnUserEntity(user.userId, sessionId)
                            finalizeLogin(vpnUser)
                            Result.Success(vpnUser)
                        } else {
                            Result.Error(context.getString(R.string.auth_login_general_error))
                        }
                    }
                }
            }
        }
    }

    private suspend fun finalizeLogin(vpnUser: VpnUser) {
        ProtonLogger.log(UserPlanChanged, "logged in: ${vpnUser.toLog()}")
        vpnUserDao.insertOrUpdate(vpnUser)
        val showConnectFeature = api.getFeature(ONBOARDING_SHOW_CONNECT_FEATURE)
        if (showConnectFeature is ApiResult.Success) {
            Storage.saveBoolean(
                OnboardingPreferences.ONBOARDING_SHOW_CONNECT,
                showConnectFeature.value.feature.value
            )
        }
        serverListUpdater.updateLocationIfVpnOff()
        appConfig.update().join()
    }

    companion object {
        const val ONBOARDING_SHOW_CONNECT_FEATURE = "OnboardingShowFirstConnection"
        private const val ERROR_CODE_NO_CONNECTIONS_ASSIGNED = 86_300
    }
}

