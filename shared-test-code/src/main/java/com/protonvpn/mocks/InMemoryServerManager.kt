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
import com.protonvpn.android.appconfig.UserCountryTimezoneBased
import com.protonvpn.android.servers.Server
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
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent

@OptIn(ExperimentalCoroutinesApi::class)
fun createInMemoryServersDataManager(
    testScope: TestScope,
    testDispatcherProvider: TestDispatcherProvider,
    initialServers: List<Server> = emptyList(),
    initialStatusId: LogicalsStatusId? = null,
    updateWithBinaryStatus: UpdateServersWithBinaryStatus = FakeUpdateServersWithBinaryStatus(),
): ServersDataManager {
    val serverStore = createInMemoryServersStore(initialServers, initialStatusId)
    return ServersDataManager(
        testDispatcherProvider,
        serverStore,
        updateWithBinaryStatus,
        testScope::currentTime
    )
}

fun createInMemoryServerManager(
    testScope: TestScope,
    testDispatcherProvider: TestDispatcherProvider,
    initialServers: List<Server>,
    initialStatusId: LogicalsStatusId? = null,
    updateWithBinaryStatus: UpdateServersWithBinaryStatus = FakeUpdateServersWithBinaryStatus(),
    physicalUserCountry: UserCountryPhysical = createNoopUserCountry(),
) =
    createInMemoryServerManager(
        testScope = testScope,
        serversDataManager =
            createInMemoryServersDataManager(
                testScope = testScope,
                testDispatcherProvider = testDispatcherProvider,
                initialServers = initialServers,
                initialStatusId = initialStatusId,
                updateWithBinaryStatus = updateWithBinaryStatus,
            ),
        physicalUserCountry = physicalUserCountry,
    )

@OptIn(ExperimentalCoroutinesApi::class)
fun createInMemoryServerManager(
    testScope: TestScope,
    serversDataManager: ServersDataManager,
    physicalUserCountry: UserCountryPhysical = createNoopUserCountry(),
): ServerManager {
    val serverManager =
        ServerManager(
            testScope.backgroundScope,
            serversDataManager,
            physicalUserCountry,
        )
    testScope.runCurrent()
    return serverManager
}

fun createNoopUserCountry() =
    UserCountryPhysical(
        TestUserCountryTelephonyBased(),
        UserCountryTimezoneBased(null),
        UserCountryIpBased(ServerListUpdaterPrefs(MockSharedPreferencesProvider()), null),
    )
