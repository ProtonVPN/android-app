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
import com.protonvpn.android.auth.data.VpnUser
import com.protonvpn.android.auth.data.VpnUserDao
import com.protonvpn.android.logging.ProtonLogger
import com.protonvpn.android.logging.UserPlanChanged
import com.protonvpn.android.logging.toLog
import com.protonvpn.android.models.login.toVpnUserEntity
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
    val currentUser: CurrentUser
) {
    sealed class Result {
        class Success(val vpnUser: VpnUser) : Result()
        class Error(val message: String) : Result()
        object AssignConnections : Result()
    }

    suspend operator fun invoke(user: User, context: Context): Result {
        val sessionId = sessionProvider.getSessionId(user.userId)
        requireNotNull(sessionId)
        return when (val vpnResult = api.getVPNInfo(sessionId)) {
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
                        val vpnUser = vpnResult.value.toVpnUserEntity(user.userId, sessionId)
                        ProtonLogger.log(UserPlanChanged, "logged in: ${vpnUser.toLog()}")
                        vpnUserDao.insertOrUpdate(vpnUser)
                        Result.Success(vpnUser)
                    }
                }
            }
        }
    }
}

