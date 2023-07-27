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
    private val gatewayServers = MockedServers.serverList.subList(0, 1)
    private val secureCoreEntryCountries = listOf(VpnCountry("CH", MockedServers.serverList.subList(1, 3)))
    private val secureCoreExitCountries = listOf(VpnCountry("JP", MockedServers.serverList.subList(1, 3)))

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
        store.vpnCountries += countries
        store.gatewayServers = gatewayServers
        store.secureCoreEntryCountries = secureCoreEntryCountries
        store.secureCoreExitCountries = secureCoreExitCountries
        store.save()
        assertTrue(testFile.exists())

        val store2 = createServersStore(testFile)
        assertEquals(countries, store2.vpnCountries)
        assertEquals(gatewayServers, store2.gatewayServers)
        assertEquals(secureCoreEntryCountries, store2.secureCoreEntryCountries)
        assertEquals(secureCoreExitCountries, store2.secureCoreExitCountries)
        store.clear()

        val store3 = createServersStore(testFile)
        assertEquals(emptyList(), store3.vpnCountries)
        assertEquals(emptyList(), store3.gatewayServers)
        assertEquals(emptyList(), store3.secureCoreEntryCountries)
        assertEquals(emptyList(), store3.secureCoreExitCountries)
        assertFalse(testFile.exists())
    }

    @Test
    fun `migration from old store`() = testScope.runTest {
        val store = createServersStore(testFile)
        store.migrate(emptyList(), countries, emptyList())

        val store2 = createServersStore(testFile)
        assertEquals(countries, store2.secureCoreEntryCountries)
    }

    @Test
    fun `recover from interrupted rename`() = testScope.runTest {
        val store = createServersStore(testFile)
        store.vpnCountries += countries
        store.save()

        // Tmp write was successful but the rename failed
        testFile.renameTo(tmpFile)

        val store2 = createServersStore(testFile)
        assertEquals(countries, store2.vpnCountries)
        assertTrue(testFile.exists())
        assertFalse(tmpFile.exists())
    }

    @Test
    fun `recover from unfinished write`() = testScope.runTest {
        val store = createServersStore(testFile)
        store.vpnCountries += countries
        store.save()

        // Tmp write was interrupted but test file exists
        tmpFile.writeText("corrupted")

        val store2 = createServersStore(testFile)
        assertEquals(countries, store2.vpnCountries)
        assertTrue(testFile.exists())
        assertFalse(tmpFile.exists())
    }

    @Test
    fun `recover from unfinished first save`() = testScope.runTest {
        val store = createServersStore(testFile)
        store.vpnCountries += countries
        store.save()

        testFile.delete()
        tmpFile.writeText("corrupted")

        val store2 = createServersStore(testFile)
        assertEquals(emptyList(), store2.vpnCountries)
    }
}
