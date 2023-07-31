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

import com.protonvpn.android.models.vpn.ServersStore
import com.protonvpn.android.models.vpn.VpnCountry
import com.protonvpn.android.utils.FileObjectStore
import com.protonvpn.test.shared.MockedServers
import com.protonvpn.test.shared.TestDispatcherProvider
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
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ServersStoreTests {

    private lateinit var testScope: TestScope
    private lateinit var testDispatcher: TestDispatcher
    private val testFile = File("servers")
    private val tmpFile = File("servers${FileObjectStore.TMP_SUFFIX}")
    private val countries = listOf(VpnCountry("US", MockedServers.serverList))
    private val servers = MockedServers.serverList

    private fun createServersStore(testFile: File = File("servers")) =
        ServersStore.create(
            testScope,
            TestDispatcherProvider(testDispatcher),
            testFile
        )

    @Before
    fun setUp() {
        testDispatcher = UnconfinedTestDispatcher()
        testScope = TestScope(testDispatcher)
        Dispatchers.setMain(testDispatcher)
        testFile.delete()
        tmpFile.delete()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        testFile.delete()
        tmpFile.delete()
    }

    @Test
    fun `basic store, read and clear`() = testScope.runTest {
        val store = createServersStore(testFile)
        store.allServers = servers

        store.save()
        assertTrue(testFile.exists())

        val store2 = createServersStore(testFile)
        assertEquals(servers, store2.allServers)
        store.clear()

        val store3 = createServersStore(testFile)
        assertEquals(emptyList(), store3.allServers)
        assertFalse(testFile.exists())
    }

    @Test
    fun `migration from old store`() = testScope.runTest {
        val store = createServersStore(testFile)
        store.migrate(emptyList(), countries, emptyList())

        val store2 = createServersStore(testFile)
        val countriesServers = countries.map { it.serverList }.flatten()
        assertEquals(countriesServers, store2.allServers)
    }

    @Test
    fun `recover from interrupted rename`() = testScope.runTest {
        val store = createServersStore(testFile)
        store.allServers += servers
        store.save()

        // Tmp write was successful but the rename failed
        testFile.renameTo(tmpFile)

        val store2 = createServersStore(testFile)
        assertEquals(servers, store2.allServers)
        assertTrue(testFile.exists())
        assertFalse(tmpFile.exists())
    }

    @Test
    fun `recover from unfinished write`() = testScope.runTest {
        val store = createServersStore(testFile)
        store.allServers += servers
        store.save()

        // Tmp write was interrupted but test file exists
        tmpFile.writeText("corrupted")

        val store2 = createServersStore(testFile)
        assertEquals(servers, store2.allServers)
        assertTrue(testFile.exists())
        assertFalse(tmpFile.exists())
    }

    @Test
    fun `recover from unfinished first save`() = testScope.runTest {
        val store = createServersStore(testFile)
        store.allServers += servers
        store.save()

        testFile.delete()
        tmpFile.writeText("corrupted")

        val store2 = createServersStore(testFile)
        assertEquals(emptyList(), store2.allServers)
    }
}
