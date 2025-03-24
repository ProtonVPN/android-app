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
import com.protonvpn.android.api.GuestHole
import com.protonvpn.android.api.ProtonApiRetroFit
import com.protonvpn.android.appconfig.AppConfig
import com.protonvpn.android.appconfig.CachedPurchaseEnabled
import com.protonvpn.android.auth.data.VpnUser
import com.protonvpn.android.di.WallClock
import com.protonvpn.android.logging.ProtonLogger
import com.protonvpn.android.logging.UserPlanChanged
import com.protonvpn.android.logging.toLog
import com.protonvpn.android.managed.ManagedConfig
import com.protonvpn.android.models.login.toVpnUserEntity
import com.protonvpn.android.ui.home.ServerListUpdater
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
    private val api: ProtonApiRetroFit,
    private val sessionProvider: SessionProvider,
    private val certificateRepository: CertificateRepository,
    private val purchaseEnabled: CachedPurchaseEnabled,
    private val appConfig: AppConfig,
    private val serverListUpdater: ServerListUpdater,
    private val guestHole: GuestHole,
    private val setVpnUser: SetVpnUser,
    @WallClock private val wallClock: () -> Long,
    private val managedConfig: ManagedConfig
) {
    sealed class Result {
        class Success(val vpnUser: VpnUser) : Result()
        class Error(val message: String) : Result()
        object AssignConnections : Result()
    }

    suspend operator fun invoke(user: User, context: Context): Result = coroutineScope {
        purchaseEnabled.forceRefresh()
        val sessionId = sessionProvider.getSessionId(user.userId)
        requireNotNull(sessionId)
        // Note: all API calls need explicit sessionId!
        val vpnInfoDeferred = async { api.getVPNInfo(sessionId) }
        val appConfigDeferred = async { appConfig.forceUpdate(user.userId) }
        val certificateDeferred = async { fetchCertificate(sessionId) }
        val autoLoginName = managedConfig.value?.username

        when (val vpnResult = vpnInfoDeferred.await()) {
            is ApiResult.Error.Http -> {
                if (vpnResult.proton?.code == ERROR_CODE_NO_CONNECTIONS_ASSIGNED) {
                    Result.AssignConnections
                } else {
                    Result.Error(vpnResult.proton?.error ?: context.getString(AuthR.string.auth_login_general_error))
                }
            }
            is ApiResult.Error ->
                Result.Error(context.getString(AuthR.string.auth_login_general_error))
            is ApiResult.Success -> {
                val vpnInfo = vpnResult.value.vpnInfo
                when {
                    vpnInfo.userTierUnknown ->
                        Result.Error(context.getString(AuthR.string.auth_login_general_error))
                    vpnInfo.hasNoConnectionsAssigned ->
                        Result.AssignConnections
                    else -> {
                        val appConfigResult = appConfigDeferred.await()
                        val certificateFetched = certificateDeferred.await()
                        if (certificateFetched && appConfigResult.isSuccess) {
                            val vpnUser = vpnResult.value.toVpnUserEntity(user.userId, sessionId, wallClock(), autoLoginName)
                            finalizeLogin(vpnUser)
                            Result.Success(vpnUser)
                        } else {
                            val appConfigError = (appConfigResult as? ApiResult.Error.Http)?.proton?.error
                            Result.Error(appConfigError ?: context.getString(AuthR.string.auth_login_general_error))
                        }
                    }
                }
            }
        }
    }

    private suspend fun fetchCertificate(sessionId: SessionId): Boolean {
        certificateRepository.generateNewKey(sessionId)
        val certificateResult = certificateRepository.updateCertificate(sessionId, false)
        return certificateResult is CertificateRepository.CertificateResult.Success
    }

    private suspend fun finalizeLogin(vpnUser: VpnUser) {
        ProtonLogger.log(UserPlanChanged, "logged in: ${vpnUser.toLog()}")
        setVpnUser(vpnUser)
        if (guestHole.isGuestHoleActive)
            serverListUpdater.updateServerList()
        guestHole.releaseNeedGuestHole(GUEST_HOLE_ID)
    }

    companion object {
        private const val ERROR_CODE_NO_CONNECTIONS_ASSIGNED = 86_300
        const val GUEST_HOLE_ID = "LOGIN_SIGNUP"
    }
}
