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
package com.protonvpn.android.quicktile

import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.profiles.data.ProfileAutoOpen
import com.protonvpn.android.profiles.data.ProfilesDao
import com.protonvpn.android.redesign.recents.data.DefaultConnection
import com.protonvpn.android.redesign.recents.usecases.ObserveDefaultConnection
import com.protonvpn.android.redesign.recents.usecases.RecentsManager
import com.protonvpn.android.vpn.VpnStatusProviderUI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QuickTileDataStoreUpdater @Inject constructor(
    private val mainScope: CoroutineScope,
    private val currentUser: CurrentUser,
    private val vpnStatusProviderUI: VpnStatusProviderUI,
    private val dataStore: QuickTileDataStore,
    private val recentsManager: RecentsManager,
    private val profilesDao: ProfilesDao,
    private val observeDefaultConnection: ObserveDefaultConnection,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    fun start() {
        combine(
            currentUser.vpnUserFlow,
            vpnStatusProviderUI.uiStatus,
        ) { vpnUser, vpnStatus -> vpnUser to vpnStatus }.flatMapLatest { (vpnUser, vpnStatus) ->
            val isLoggedIn = vpnUser != null
            val isPlus = vpnUser?.isUserPlusOrAbove == true
            isAutoOpenForDefaultConnectionFlow(isPlus).map { isAutoOpenForDefaultConnection ->
                QuickTileDataStore.Data(vpnStatus.state.toTileState(), isLoggedIn, isAutoOpenForDefaultConnection, vpnStatus.server?.serverName)
            }
        }
        .distinctUntilChanged()
        .onEach { data -> dataStore.store(data) }
        .launchIn(mainScope)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun isAutoOpenForDefaultConnectionFlow(isPlus: Boolean): Flow<Boolean> =
        if (!isPlus) {
            flowOf(false)
        } else combine(
            observeDefaultConnection(),
            recentsManager.getMostRecentConnection(),
        ) { defaultConnection, mostRecentConnection ->
            defaultConnection to mostRecentConnection
        }.flatMapLatest { (defaultConnection, mostRecentConnection) ->
            val profileId = when (defaultConnection) {
                DefaultConnection.FastestConnection -> null
                DefaultConnection.LastConnection ->
                    mostRecentConnection?.connectIntent?.profileId
                is DefaultConnection.Recent ->
                    recentsManager.getRecentById(defaultConnection.recentId)?.connectIntent?.profileId
            }
            if (profileId == null) {
                flowOf(false)
            } else {
                profilesDao.getProfileByIdFlow(profileId).map { profile ->
                    profile?.autoOpen !is ProfileAutoOpen.None
                }
            }
        }
}
