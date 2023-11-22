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

import com.protonvpn.android.appconfig.periodicupdates.IsLoggedIn
import com.protonvpn.android.vpn.VpnStatusProviderUI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QuickTileDataStoreUpdater @Inject constructor(
    private val mainScope: CoroutineScope,
    @IsLoggedIn private val loggedIn: Flow<Boolean>,
    private val vpnStatusProviderUI: VpnStatusProviderUI,
    private val dataStore: QuickTileDataStore
) {
    fun start() {
        combine(
            loggedIn,
            vpnStatusProviderUI.status,
        ) { isLoggedIn, vpnStatus ->
            QuickTileDataStore.Data(vpnStatus.state.toTileState(), isLoggedIn, vpnStatus.server?.serverName)
        }.onEach { data ->
            dataStore.store(data)
        }.launchIn(mainScope)
    }
}