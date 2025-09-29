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

package com.protonvpn.app.redesign.countries

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.protonvpn.android.redesign.countries.Translator
import com.protonvpn.android.utils.ServerManager
import com.protonvpn.android.utils.Storage
import com.protonvpn.mocks.createInMemoryServerManager
import com.protonvpn.test.shared.MockSharedPreference
import com.protonvpn.test.shared.TestDispatcherProvider
import com.protonvpn.test.shared.createServer
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TranslatorTests {

    @get:Rule
    val rule = InstantTaskExecutorRule()

    private lateinit var testScope: TestScope

    private lateinit var serverManager: ServerManager

    @Before
    fun setup() {
        Storage.setPreferences(MockSharedPreference())
        val dispatcher = UnconfinedTestDispatcher()
        testScope = TestScope(dispatcher)

        serverManager = createInMemoryServerManager(
            testScope,
            TestDispatcherProvider(dispatcher),
            supportsProtocol = mockk(relaxed = true),
            emptyList()
        )
    }

    private fun TestScope.createTranslator() = Translator(backgroundScope, serverManager)

    @Test
    fun `city names are translated correctly`() = testScope.runTest {
        val servers = listOf(
            createServer(city = "Zurich", translations = mapOf("City" to "Zurych")),
            createServer(city = "New York", translations = mapOf("City" to "Nowy Jork"))
        )
        serverManager.setServers(servers, null, "pl")
        val translator = createTranslator()

        assertEquals("Zurych", translator.getCity("Zurich"))
        assertEquals("Nowy Jork", translator.getCity("New York"))
    }

    @Test
    fun `state names are translated correctly`() = testScope.runTest {
        val servers = listOf(
            createServer(state = "California", translations = mapOf("State" to "Kalifornia")),
            createServer(state = "New York", translations = mapOf("State" to "Nowy Jork"))
        )
        serverManager.setServers(servers, null, "pl")
        val translator = createTranslator()

        assertEquals("Kalifornia", translator.getState("California"))
        assertEquals("Nowy Jork", translator.getState("New York"))
    }

    @Test
    fun `when no translation is available then original name is returned`() = testScope.runTest {
        val servers = listOf(
            createServer(city = "Zurich", translations = null),
            createServer(city = "New York", translations = mapOf("City" to null)),
            createServer(city = "Warsaw", translations = mapOf("City" to "")),
            createServer(state = "California", translations = null),
        )
        serverManager.setServers(servers, null, "pl")
        val translator = createTranslator()

        assertEquals("Zurich", translator.getCity("Zurich"))
        assertEquals("New York", translator.getCity("New York"))
        assertEquals("Warsaw", translator.getCity("Warsaw"))
        assertEquals("California", translator.getState("California"))
    }

    @Test
    fun `when server list changes then translations are updated`() = testScope.runTest {
        val serversPl = listOf(
            createServer(city = "New York", translations = mapOf("City" to "Nowy Jork")),
            createServer(state = "North Carolina", translations = mapOf("State" to "Północna Karolina"))
        )
        val serversEs = listOf(
            createServer(city = "New York", translations = mapOf("City" to "Nueva York")),
            createServer(state = "North Carolina", translations = mapOf("State" to "Carolina del Norte"))
        )

        serverManager.setServers(serversPl, null, "pl")
        val translator = createTranslator()
        assertEquals("Nowy Jork", translator.getCity("New York"))
        assertEquals("Północna Karolina", translator.getState("North Carolina"))

        serverManager.setServers(serversEs, null, "es")
        assertEquals("Nueva York", translator.getCity("New York"))
        assertEquals("Carolina del Norte", translator.getState("North Carolina"))
    }
}
