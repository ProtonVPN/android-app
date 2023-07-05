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
import com.protonvpn.android.redesign.recents.data.RecentsDao
import com.protonvpn.android.redesign.vpn.ConnectIntent
import com.protonvpn.android.utils.ServerManager
import com.protonvpn.android.utils.flatMapLatestNotNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject
import javax.inject.Singleton

private const val MAX_UNPINNED_RECENTS = 6

@Singleton
class RecentsListValidator @Inject constructor(
    mainScope: CoroutineScope,
    recentsDao: RecentsDao,
    serverManager: ServerManager,
    currentUser: CurrentUser
) {

    private val removeRecentsWithNoServer = combine(
        serverManager.serverListVersion,
        recentsDao.getServerRecentsForAllUsers()
    ) { _, serverRecents ->
        serverRecents.filterNot {
            val intent = it.connectIntent
            intent is ConnectIntent.Server && serverManager.getServerById(intent.serverId) != null
        }
    }.onEach { intentsWithNoServer ->
        if (intentsWithNoServer.isNotEmpty()) {
            println("Deleting ${intentsWithNoServer.size}")
            recentsDao.delete(intentsWithNoServer.map { it.id })
            println("Deleted ${intentsWithNoServer.size}")
        }
    }

    private val removeUnpinnedRecentsOverLimit = currentUser.userFlow.flatMapLatestNotNull { user ->
        recentsDao.getUnpinnedCount(user.userId)
            .onEach { count ->
                // Note: this is doable with a trigger. Doing it this way truncates the list after an update has
                // been emitted potentially showing the longer list for a split second.
                if (count > MAX_UNPINNED_RECENTS) {
                    recentsDao.deleteExcessUnpinnedRecents(user.userId, MAX_UNPINNED_RECENTS)
                }
            }
    }

    init {
        removeRecentsWithNoServer.launchIn(mainScope)
        removeUnpinnedRecentsOverLimit.launchIn(mainScope)
    }
}
