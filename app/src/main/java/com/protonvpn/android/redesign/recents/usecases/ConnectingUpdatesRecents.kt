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
import com.protonvpn.android.redesign.recents.data.RecentsDao
import com.protonvpn.android.utils.flatMapLatestNotNull
import com.protonvpn.android.vpn.VpnState
import com.protonvpn.android.vpn.VpnStatusProviderUI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConnectingUpdatesRecents @Inject constructor(
    mainScope: CoroutineScope,
    vpnStatusProvider: VpnStatusProviderUI,
    recentsDao: RecentsDao,
    currentUser: CurrentUser,
    @WallClock clock: () -> Long
) {
    init {
        currentUser.userFlow.flatMapLatestNotNull { user ->
            vpnStatusProvider.uiStatus
                .mapNotNull { vpnStatus ->
                    vpnStatus.connectIntent.takeIf { vpnStatus.state !is VpnState.Disabled }
                }
                .distinctUntilChanged()
                .onEach { connectIntent ->
                    recentsDao.insertOrUpdateForConnection(user.userId, connectIntent, clock())
                }
        }.launchIn(mainScope)
    }
}
