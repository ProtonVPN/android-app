/*
 * Copyright (c) 2023 Proton AG
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

@file:OptIn(ExperimentalCoroutinesApi::class)

package com.protonvpn.app.vpn

import com.protonvpn.android.servers.Server
import com.protonvpn.android.servers.ServersStore
import com.protonvpn.android.servers.ServersDataManager
import com.protonvpn.test.shared.InMemoryObjectStore
import com.protonvpn.test.shared.TestDispatcherProvider
import com.protonvpn.test.shared.createServer
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ServersDataManagerTests {

    private lateinit var testScope: TestScope
    private lateinit var testDispatcher: TestDispatcher
    private lateinit var store: ServersStore
    private lateinit var manager: ServersDataManager

    @Before
    fun setUp() {
        testDispatcher = UnconfinedTestDispatcher()
        testScope = TestScope(testDispatcher)
        Dispatchers.setMain(testDispatcher)
        store = ServersStore(InMemoryObjectStore())
        manager = ServersDataManager(
            TestDispatcherProvider(testDispatcher),
            store,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `retainIDs servers are retained when replacing`() = testScope.runTest {
        manager.replaceServers(
            listOf(
                createServer(serverId = "1"),
                createServer(serverId = "2"),
                createServer(serverId = "3"),
            ),
            retainIDs = emptySet()
        )
        manager.replaceServers(
            listOf(
                createServer(serverId = "1"),
                createServer(serverId = "4"),
            ),
            retainIDs = setOf("1", "2")
        )
        assertEquals(setOf("1", "2", "4"), manager.allServers.toIds())
    }

    @Test
    fun `invisible servers are filtered out`() = testScope.runTest {
        val servers = listOf(
            createServer(serverId = "1", exitCountry = "PL", isVisible = true),
            createServer(serverId = "2", exitCountry = "PL", isVisible = false),
            createServer(serverId = "3", exitCountry = "CH", gatewayName = "company", isVisible = true),
            createServer(serverId = "4", exitCountry = "CH", gatewayName = "company", isVisible = false),
            createServer(serverId = "5", entryCountry = "CH", exitCountry = "PL", isVisible = true),
            createServer(serverId = "6", entryCountry = "CH", exitCountry = "PL", isVisible = false),
        )
        manager.replaceServers(servers, emptySet())

        assertEquals(setOf("1", "3", "5"), manager.allServers.toIds())
        assertEquals(setOf("1"), manager.vpnCountries.find { it.flag == "PL" }?.serverList?.toIds())
        assertEquals(setOf("3"), manager.gateways.find { it.name() == "company" }?.serverList?.toIds())
        assertEquals(setOf("5"), manager.secureCoreExitCountries.find { it.flag == "PL" }?.serverList?.toIds())
    }

    private fun Iterable<Server>.toIds(): Set<String> = this.map { it.serverId }.toSet()
}
