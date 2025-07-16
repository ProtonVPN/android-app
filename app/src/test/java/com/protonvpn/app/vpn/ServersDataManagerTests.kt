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
import com.protonvpn.mocks.FakeUpdateServersWithBinaryStatus
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

    private lateinit var fakeServerStateUpdater: FakeUpdateServersWithBinaryStatus
    private lateinit var store: ServersStore
    private lateinit var testScope: TestScope
    private lateinit var testDispatcher: TestDispatcher

    private lateinit var manager: ServersDataManager

    @Before
    fun setUp() {
        testDispatcher = UnconfinedTestDispatcher()
        testScope = TestScope(testDispatcher)
        Dispatchers.setMain(testDispatcher)
        store = ServersStore(InMemoryObjectStore())
        fakeServerStateUpdater = FakeUpdateServersWithBinaryStatus()
        manager = ServersDataManager(
            TestDispatcherProvider(testDispatcher),
            store,
            fakeServerStateUpdater,
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
            null,
            retainIDs = emptySet()
        )
        manager.replaceServers(
            listOf(
                createServer(serverId = "1"),
                createServer(serverId = "4"),
            ),
            null,
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
        val statusId = "status ID"
        manager.replaceServers(servers, statusId, emptySet())

        assertEquals(setOf("1", "3", "5"), manager.allServers.toIds())
        assertEquals(setOf("1"), manager.vpnCountries.find { it.flag == "PL" }?.serverList?.toIds())
        assertEquals(setOf("3"), manager.gateways.find { it.name() == "company" }?.serverList?.toIds())
        assertEquals(setOf("5"), manager.secureCoreExitCountries.find { it.flag == "PL" }?.serverList?.toIds())

        fakeServerStateUpdater.mapsAllServers { it.copy(isVisible = true) }
        manager.updateBinaryLoads(statusId, ByteArray(0))

        assertEquals(setOf("1", "2", "3", "4", "5", "6"), manager.allServers.toIds())
        assertEquals(setOf("1", "2"), manager.vpnCountries.find { it.flag == "PL" }?.serverList?.toIds())
        assertEquals(setOf("3", "4"), manager.gateways.find { it.name() == "company" }?.serverList?.toIds())
        assertEquals(setOf("5", "6"), manager.secureCoreExitCountries.find { it.flag == "PL" }?.serverList?.toIds())
    }

    @Test
    fun `binary status update only applies for matching status ID`() = testScope.runTest {
        val servers = listOf(createServer("1", loadPercent = 50f), createServer("2", loadPercent = 50f))

        val newLoad = 10f
        val statusIdOld = "status 1"
        val statusIdCurrent = "status 2"
        fakeServerStateUpdater.mapsAllServers { it.copy(load = newLoad) }
        manager.replaceServers(servers, statusIdCurrent, emptySet())
        manager.updateBinaryLoads(statusIdOld, ByteArray(0))

        assertEquals(listOf(50f, 50f), manager.allServers.map { it.load })

        manager.updateBinaryLoads(statusIdCurrent, ByteArray(0))
        assertEquals(listOf(newLoad, newLoad), manager.allServers.map { it.load })
    }

    private fun Iterable<Server>.toIds(): Set<String> = this.map { it.serverId }.toSet()
}
