/*
 * Copyright (c) 2025 Proton AG
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

package com.protonvpn.android.profiles.usecases

import com.protonvpn.android.di.WallClock
import com.protonvpn.android.profiles.data.ProfilesDao
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
class UpdateProfileLastConnected @Inject constructor(
    private val mainScope: CoroutineScope,
    private val vpnStatusProviderUI: VpnStatusProviderUI,
    private val profilesDao: ProfilesDao,
    @WallClock private val wallClock: () -> Long,
) {
    fun start() {
        vpnStatusProviderUI.status
            .mapNotNull { status ->
                val profileId = status.connectIntent?.profileId
                if (profileId != null && status.state is VpnState.Connected) profileId
                else null
            }
            .distinctUntilChanged()
            .onEach { profileId ->
                profilesDao.updateLastConnectedAt(profileId, wallClock())
            }.launchIn(mainScope)
    }
}