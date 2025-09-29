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

import com.protonvpn.android.appconfig.UserCountryIpBased
import com.protonvpn.android.appconfig.UserCountryPhysical
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.servers.Server
import com.protonvpn.android.models.vpn.usecase.SupportsProtocol
import com.protonvpn.android.servers.ServersDataManager
import com.protonvpn.android.servers.UpdateServersWithBinaryStatus
import com.protonvpn.android.servers.api.LogicalsStatusId
import com.protonvpn.android.ui.home.ServerListUpdaterPrefs
import com.protonvpn.android.utils.ServerManager
import com.protonvpn.test.shared.MockSharedPreferencesProvider
import com.protonvpn.test.shared.TestDispatcherProvider
import com.protonvpn.test.shared.TestUserCountryTelephonyBased
import com.protonvpn.test.shared.createInMemoryServersStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent

@OptIn(ExperimentalCoroutinesApi::class)
fun createInMemoryServerManager(
    testScope: TestScope,
    testDispatcherProvider: TestDispatcherProvider,
    supportsProtocol: SupportsProtocol,
    initialServers: List<Server>,
    initialStatusId: LogicalsStatusId? = null,
    updateWithBinaryStatus: UpdateServersWithBinaryStatus = FakeUpdateServersWithBinaryStatus(),
    builtInGuestHoles: List<Server> = emptyList(),
    physicalUserCountry: UserCountryPhysical = createNoopUserCountry(),
): ServerManager {
    val serverStore = createInMemoryServersStore(initialServers, initialStatusId)
    val serversDataManager = ServersDataManager(
        testDispatcherProvider,
        serverStore,
        updateWithBinaryStatus,
    )
    val serverManager = ServerManager(
        testScope.backgroundScope,
        testScope::currentTime,
        supportsProtocol,
        serversDataManager,
        physicalUserCountry,
    )
    testScope.launch {
        serverManager.setServers(initialServers, initialStatusId, "en")
    }
    testScope.runCurrent()
    serverManager.setBuiltInGuestHoleServersForTesting(builtInGuestHoles)
    return serverManager
}

private fun createNoopUserCountry() =
    UserCountryPhysical(
        TestUserCountryTelephonyBased(),
        UserCountryIpBased(ServerListUpdaterPrefs(MockSharedPreferencesProvider()), null),
    )
