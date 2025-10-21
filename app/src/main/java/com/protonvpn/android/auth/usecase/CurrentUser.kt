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

import com.protonvpn.android.auth.data.VpnUser
import com.protonvpn.android.auth.data.VpnUserDao
import com.protonvpn.android.concurrency.VpnDispatcherProvider
import com.protonvpn.android.utils.withPrevious
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.cancellable
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import me.proton.core.account.domain.entity.AccountState
import me.proton.core.accountmanager.domain.AccountManager
import me.proton.core.accountmanager.domain.getPrimaryAccount
import me.proton.core.domain.entity.SessionUserId
import me.proton.core.network.domain.session.SessionId
import me.proton.core.user.domain.UserManager
import me.proton.core.user.domain.entity.User
import me.proton.core.util.kotlin.takeIfNotBlank
import javax.inject.Inject
import javax.inject.Singleton

data class PartialJointUserInfo(val user: User?, val vpnUser: VpnUser?, val sessionId: SessionId?) {
    override fun toString(): String {
        return "PartialJointUserInt(user=\"${user?.userId}\", vpnUser=\"${vpnUser?.userId}, sessionId=$sessionId)"
    }
}
data class FullJointUserInfo(val user: User, val vpnUser: VpnUser, val sessionId: SessionId)
fun PartialJointUserInfo.toJointUserInfo() =
    if (user == null || vpnUser == null || sessionId == null) null
    else FullJointUserInfo(user, vpnUser, sessionId)

fun PartialJointUserInfo.hasConnectionsAssigned(): Boolean = vpnUser != null

interface CurrentUserProvider {
    fun invalidateCache()
    val partialJointUserFlow: Flow<PartialJointUserInfo>
}

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class DefaultCurrentUserProvider @Inject constructor(
    mainScope: CoroutineScope,
    dispatcherProvider: VpnDispatcherProvider,
    accountManager: AccountManager,
    vpnUserDao: VpnUserDao,
    userManager: UserManager
) : CurrentUserProvider {

    private val invalidate = MutableStateFlow(0L)
    private val cachedInfoFlow = MutableStateFlow<PartialJointUserInfo?>(null)

    init {
        mainScope.launch(dispatcherProvider.infiniteIo) {
            // Each time invalidate emits we'll restart collection of flow that will provide
            // cached values to cachedInfoFlow (for which null value means there's no cached
            // value atm)
            invalidate.collectLatest { version ->
                accountManager.getPrimaryAccount()
                    .map { account ->
                        val activeAccount = account?.takeIf { it.state != AccountState.Disabled }
                        activeAccount?.userId to activeAccount?.sessionId
                    }
                    .distinctUntilChanged()
                    .flatMapLatest { (userId, sessionId) ->
                        when (userId) {
                            null -> flowOf(PartialJointUserInfo(null, null, null))
                            else -> combine(
                                userManager.observeUser(SessionUserId(userId.id)),
                                vpnUserDao.getByUserId(userId)
                            ) { accountUser, vpnUser ->
                                PartialJointUserInfo(accountUser, vpnUser, sessionId)
                            }
                        }
                    }
                    .distinctUntilChanged()
                    .cancellable()
                    .collect {
                        if (invalidate.value == version)
                            cachedInfoFlow.value = it
                    }
            }
        }
    }

    override val partialJointUserFlow: Flow<PartialJointUserInfo> = cachedInfoFlow.filterNotNull()

    override fun invalidateCache() {
        cachedInfoFlow.value = null
        invalidate.update { it + 1 }
    }
}

// Class allowing to access user-related data synchronously - current VPN code requires broad refactoring to deal
// with async access to user.
@Singleton
class CurrentUser @Inject constructor(
    private val provider: CurrentUserProvider
) {
    val vpnUserFlow = provider.partialJointUserFlow.map { it.vpnUser }.distinctUntilChanged()
    val userFlow = provider.partialJointUserFlow.map { it.user }.distinctUntilChanged()
    val sessionIdFlow = provider.partialJointUserFlow.map { it.sessionId }.distinctUntilChanged()

    val partialJointUserFlow = provider.partialJointUserFlow
    // Will serve only users that have non-null user and vpnUser and sessionId
    val jointUserFlow = provider.partialJointUserFlow.map { it.toJointUserInfo() }.distinctUntilChanged()

    val eventPartialLogin = partialJointUserFlow.map { it.user }.withPrevious()
        .filter { (previous, new) -> new?.userId != null && previous?.userId != new.userId }

    val eventVpnLogin = vpnUserFlow.withPrevious()
        .filter { (previous, new) ->
            new?.userId != null && previous?.userId != new.userId
        }
        .map { (_, new) -> new }

    val hasConnectionsAssignedFlow = provider.partialJointUserFlow
        .map(PartialJointUserInfo::hasConnectionsAssigned)
        .distinctUntilChanged()

    suspend fun vpnUser() = vpnUserFlow.first()
    suspend fun user() = userFlow.first()
    suspend fun sessionId() = sessionIdFlow.first()
    suspend fun isLoggedIn() = sessionIdFlow.first() != null

    @Deprecated("use suspending version of this fun", replaceWith = ReplaceWith("vpnUser"))
    fun vpnUserBlocking() = runBlocking { vpnUserFlow.first() }

    @Deprecated("use suspending version of this fun", replaceWith = ReplaceWith("vpnUser"))
    fun vpnUserCached() = vpnUserBlocking()

    fun invalidateCache() {
        provider.invalidateCache()
    }
}

fun User.uiName() =
    displayName?.takeIfNotBlank() ?: name?.takeIfNotBlank() ?: email
