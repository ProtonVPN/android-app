/*
 * Copyright (c) 2024 Proton AG
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

package com.protonvpn.mocks

import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.models.vpn.Server
import com.protonvpn.android.models.vpn.usecase.SupportsProtocol
import com.protonvpn.android.servers.ServersDataManager
import com.protonvpn.android.ui.home.GetUserCountry
import com.protonvpn.android.ui.home.ServerListUpdaterPrefs
import com.protonvpn.android.utils.ServerManager
import com.protonvpn.test.shared.MockSharedPreferencesProvider
import com.protonvpn.test.shared.TestDispatcherProvider
import com.protonvpn.test.shared.createInMemoryServersStore
import com.protonvpn.test.shared.createIsImmutableServerListEnabled
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent

fun createInMemoryServerManager(
    testScope: TestScope,
    testDispatcherProvider: TestDispatcherProvider,
    supportsProtocol: SupportsProtocol,
    currentUser: CurrentUser,
    initialServers: List<Server>,
    builtInGuestHoles: List<Server> = emptyList(),
    immutableServerList: Boolean = true,
): ServerManager {
    val serverStore = createInMemoryServersStore(initialServers)
    val serversDataManager = ServersDataManager(
        testScope.backgroundScope,
        testDispatcherProvider,
        serverStore
    ) { createIsImmutableServerListEnabled(immutableServerList) }
    val getUserCountry = GetUserCountry(ServerListUpdaterPrefs(MockSharedPreferencesProvider()))
    val serverManager = ServerManager(
        testScope.backgroundScope,
        currentUser,
        testScope::currentTime,
        supportsProtocol,
        serversDataManager,
        getUserCountry,
    )
    testScope.launch {
        serverManager.setServers(initialServers, "en")
    }
    testScope.runCurrent()
    serverManager.setBuiltInGuestHoleServersForTesting(builtInGuestHoles)
    return serverManager
}
