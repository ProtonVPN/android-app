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

package com.protonvpn.android.auth

import android.content.Context
import com.protonvpn.android.R
import com.protonvpn.android.api.ProtonApiRetroFit
import com.protonvpn.android.models.config.UserData
import com.protonvpn.android.vpn.CertificateRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import me.proton.core.accountmanager.domain.AccountManager
import me.proton.core.auth.domain.usecase.SetupAccountCheck
import me.proton.core.auth.presentation.DefaultUserCheck
import me.proton.core.network.domain.ApiResult
import me.proton.core.network.domain.session.SessionProvider
import me.proton.core.user.domain.UserManager
import me.proton.core.user.domain.entity.User
import me.proton.core.util.kotlin.takeIfNotBlank

class VpnUserCheck(
    val mainScope: CoroutineScope,
    val api: ProtonApiRetroFit,
    val userData: UserData,
    val context: Context,
    val accountManager: AccountManager,
    userManager: UserManager,
    val certificateRepository: CertificateRepository,
    private val sessionProvider: SessionProvider
) : DefaultUserCheck(context, accountManager, userManager) {

    override suspend fun invoke(user: User): SetupAccountCheck.UserCheckResult {
        val result = super.invoke(user)
        if (result != SetupAccountCheck.UserCheckResult.Success)
            return result

        val sessionId = sessionProvider.getSessionId(user.userId)
        requireNotNull(sessionId)
        userData.setSessionId(sessionId)

        return when (val vpnResult = api.getVPNInfo()) {
            is ApiResult.Error -> {
                userData.logout()
                SetupAccountCheck.UserCheckResult.Error(
                    context.getString(R.string.auth_login_general_error))
            }
            is ApiResult.Success -> {
                val vpnInfo = vpnResult.value.vpnInfo
                if (vpnInfo.userTierUnknown) {
                    userData.logout()
                    SetupAccountCheck.UserCheckResult.Error(context.getString(R.string.auth_login_general_error))
                } else {
                    userData.setLoggedIn(sessionId, vpnResult.value)
                    userData.user = user.email?.takeIfNotBlank()
                        ?: user.name?.takeIfNotBlank()
                        ?: vpnResult.value.vpnUserName

                    mainScope.launch {
                        certificateRepository.generateNewKey(sessionId)
                    }

                    SetupAccountCheck.UserCheckResult.Success
                }
            }
        }
    }
}
