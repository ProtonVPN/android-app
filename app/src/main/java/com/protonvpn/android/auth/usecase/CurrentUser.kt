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

import com.protonvpn.android.auth.data.VpnUser
import com.protonvpn.android.auth.data.VpnUserDao
import com.protonvpn.android.utils.SyncStateFlow
import com.protonvpn.android.utils.withPrevious
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import me.proton.core.accountmanager.domain.AccountManager
import me.proton.core.accountmanager.domain.getPrimaryAccount
import me.proton.core.domain.arch.DataResult
import me.proton.core.domain.entity.SessionUserId
import me.proton.core.network.domain.session.SessionId
import me.proton.core.user.domain.UserManager
import me.proton.core.user.domain.entity.User
import me.proton.core.util.kotlin.takeIfNotBlank
import javax.inject.Inject
import javax.inject.Singleton

interface CurrentUserProvider {
    val vpnUserFlow: Flow<VpnUser?>
    val userFlow: Flow<User?>
    val sessionIdFlow: Flow<SessionId?>
}

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class DefaultCurrentUserProvider @Inject constructor(
    accountManager: AccountManager,
    vpnUserDao: VpnUserDao,
    private val userManager: UserManager
) : CurrentUserProvider {
    override val vpnUserFlow = accountManager.getPrimaryUserId().flatMapLatest { userId ->
        userId?.let { vpnUserDao.getByUserId(it) } ?: flowOf(null)
    }.distinctUntilChanged()

    override val userFlow = accountManager.getPrimaryUserId().flatMapLatest { userId ->
        if (userId == null) {
            flowOf(null)
        } else userManager.getUserFlow(SessionUserId(userId.id)).map {
            (it as? DataResult.Success)?.value
        }
    }.distinctUntilChanged()

    override val sessionIdFlow = accountManager.getPrimaryAccount().map { it?.sessionId }
}

// Class allowing to access user-related data synchronously - current VPN code requires broad refactoring to deal
// with async access to user.
@Singleton
class CurrentUser @Inject constructor(
    mainScope: CoroutineScope,
    provider: CurrentUserProvider
) {
    val vpnUserFlow = provider.vpnUserFlow
    val userFlow = provider.userFlow
    val sessionIdFlow = provider.sessionIdFlow

    val eventVpnLogin =
        vpnUserFlow.withPrevious().filter { (previous, new) -> previous == null && new != null }.map { (_, new) -> new }

    private val vpnUserState = SyncStateFlow(mainScope, vpnUserFlow)

    suspend fun vpnUser() = vpnUserFlow.first()
    suspend fun user() = userFlow.first()
    suspend fun sessionId() = sessionIdFlow.first()
    suspend fun isLoggedIn() = sessionIdFlow.first() != null

    @Deprecated("use suspending version of this fun")
    fun vpnUserCached() = vpnUserState.value

    @Deprecated("use suspending version of this fun")
    fun isLoggedInCached() = vpnUserState.value != null
}

fun User.uiName() =
    displayName?.takeIfNotBlank() ?: name?.takeIfNotBlank() ?: email
