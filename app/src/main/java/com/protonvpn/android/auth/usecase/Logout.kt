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

import dagger.Reusable
import kotlinx.coroutines.flow.first
import me.proton.core.accountmanager.domain.AccountManager
import me.proton.core.accountmanager.domain.getPrimaryAccount
import javax.inject.Inject

@Reusable
class Logout @Inject constructor(
    val accountManager: AccountManager,
    val onSessionClosed: OnSessionClosed,
    val setVpnUser: SetVpnUser,
) {
    suspend operator fun invoke() {
        accountManager.getPrimaryAccount().first()?.let { account ->
            setVpnUser(null) // Uses CurrentUser internally, must be called first.
            onSessionClosed(account)
        }
    }
}
