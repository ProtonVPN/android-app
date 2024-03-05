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
import com.protonvpn.android.settings.data.EffectiveCurrentUserSettingsCached
import com.protonvpn.android.settings.data.LocalUserSettings
import com.protonvpn.android.utils.ServerManager
import com.protonvpn.android.utils.Storage
import com.protonvpn.test.shared.MockSharedPreference
import com.protonvpn.test.shared.createInMemoryServersStore
import com.protonvpn.test.shared.createServer
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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
        testScope = TestScope(UnconfinedTestDispatcher())
        val settings = EffectiveCurrentUserSettingsCached(MutableStateFlow(LocalUserSettings.Default))

        serverManager = ServerManager(
            testScope.backgroundScope,
            settings,
            mockk(),
            { 0 },
            mockk(relaxed = true),
            createInMemoryServersStore(),
            mockk()
        )
    }

    private fun TestScope.createTranslator() = Translator(backgroundScope, serverManager)

    @Test
    fun `city names are translated correctly`() = testScope.runTest {
        val servers = listOf(
            createServer(city = "Zurich", translations = mapOf("City" to "Zurych")),
            createServer(city = "New York", translations = mapOf("City" to "Nowy Jork"))
        )
        serverManager.setServers(servers, "pl")
        val translator = createTranslator()

        assertEquals("Zurych", translator.getCity("Zurich"))
        assertEquals("Nowy Jork", translator.getCity("New York"))
    }

    @Test
    fun `region names are translated correctly`() = testScope.runTest {
        val servers = listOf(
            createServer(region = "California", translations = mapOf("Region" to "Kalifornia")),
            createServer(region = "New York", translations = mapOf("Region" to "Nowy Jork"))
        )
        serverManager.setServers(servers, "pl")
        val translator = createTranslator()

        assertEquals("Kalifornia", translator.getRegion("California"))
        assertEquals("Nowy Jork", translator.getRegion("New York"))
    }

    @Test
    fun `when no translation is available then original name is returned`() = testScope.runTest {
        val servers = listOf(
            createServer(city = "Zurich", translations = null),
            createServer(city = "New York", translations = mapOf("City" to null)),
            createServer(city = "Warsaw", translations = mapOf("City" to "")),
            createServer(region = "California", translations = null),
        )
        serverManager.setServers(servers, "pl")
        val translator = createTranslator()

        assertEquals("Zurich", translator.getCity("Zurich"))
        assertEquals("New York", translator.getCity("New York"))
        assertEquals("Warsaw", translator.getCity("Warsaw"))
        assertEquals("California", translator.getRegion("California"))
    }

    @Test
    fun `when server list changes then translations are updated`() = testScope.runTest {
        val serversPl = listOf(
            createServer(city = "New York", translations = mapOf("City" to "Nowy Jork")),
            createServer(region = "North Carolina", translations = mapOf("Region" to "Północna Karolina"))
        )
        val serversEs = listOf(
            createServer(city = "New York", translations = mapOf("City" to "Nueva York")),
            createServer(region = "North Carolina", translations = mapOf("Region" to "Carolina del Norte"))
        )

        serverManager.setServers(serversPl, "pl")
        val translator = createTranslator()
        assertEquals("Nowy Jork", translator.getCity("New York"))
        assertEquals("Północna Karolina", translator.getRegion("North Carolina"))

        serverManager.setServers(serversEs, "es")
        assertEquals("Nueva York", translator.getCity("New York"))
        assertEquals("Carolina del Norte", translator.getRegion("North Carolina"))
    }
}
