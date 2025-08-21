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

package com.protonvpn.android.auth

import android.content.Context
import com.protonvpn.android.auth.usecase.VpnLogin
import me.proton.core.accountmanager.domain.AccountManager
import me.proton.core.auth.domain.usecase.PostLoginAccountSetup
import me.proton.core.auth.presentation.DefaultUserCheck
import me.proton.core.user.domain.UserManager
import me.proton.core.user.domain.entity.User

class VpnUserCheck(
    private val context: Context,
    accountManager: AccountManager,
    userManager: UserManager,
    private val vpnLoginUseCase: VpnLogin
) : DefaultUserCheck(context, accountManager, userManager) {

    override suspend fun invoke(user: User): PostLoginAccountSetup.UserCheckResult {
        val result = super.invoke(user)
        if (result != PostLoginAccountSetup.UserCheckResult.Success)
            return result

        return when (val vpnLoginResult = vpnLoginUseCase(user, context)) {
            is VpnLogin.Result.Success ->
                PostLoginAccountSetup.UserCheckResult.Success
            is VpnLogin.Result.Error ->
                PostLoginAccountSetup.UserCheckResult.Error(vpnLoginResult.message)
        }
    }
}
