/*
 * Copyright (c) 2023. Proton AG
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

package com.protonvpn.android.redesign.recents.usecases

import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.di.WallClock
import com.protonvpn.android.redesign.recents.data.RecentConnection
import com.protonvpn.android.redesign.recents.data.RecentsDao
import com.protonvpn.android.utils.flatMapLatestNotNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages recents of the currently logged in user.
 */
@Singleton
class RecentsManager @Inject constructor(
    currentUser: CurrentUser,
    private val mainScope: CoroutineScope,
    private val recentsDao: RecentsDao,
    @WallClock private val clock: () -> Long,
) {
    private val currentVpnUserFlow = currentUser.vpnUserFlow

    fun getRecentsList(limit: Int = -1): Flow<List<RecentConnection>> = currentVpnUserFlow
        .flatMapLatestNotNull { user ->
            recentsDao.getRecentsList(user.userId, limit)
        }

    fun getMostRecentConnection(): Flow<RecentConnection?> = currentVpnUserFlow
        .flatMapLatestNotNull { user ->
            recentsDao.getMostRecentConnection(user.userId)
        }

    suspend fun getRecentById(id: Long): RecentConnection? = recentsDao.getById(id)

    fun pin(itemId: Long) {
        mainScope.launch {
            recentsDao.pin(itemId, clock())
        }
    }

    fun unpin(itemId: Long) {
        mainScope.launch {
            recentsDao.unpin(itemId)
        }
    }

    fun remove(itemId: Long) {
        mainScope.launch {
            recentsDao.delete(itemId)
        }
    }
}
