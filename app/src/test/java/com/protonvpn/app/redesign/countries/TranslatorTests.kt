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
import com.protonvpn.android.redesign.CountryId
import com.protonvpn.android.redesign.countries.Translator
import com.protonvpn.android.redesign.countries.city
import com.protonvpn.android.redesign.countries.state
import com.protonvpn.android.utils.Storage
import com.protonvpn.test.shared.InMemoryObjectStore
import com.protonvpn.test.shared.MockSharedPreference
import com.protonvpn.test.shared.createServer
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

    private lateinit var translator: Translator

    @Before
    fun setup() {
        Storage.setPreferences(MockSharedPreference())
        val dispatcher = UnconfinedTestDispatcher()
        testScope = TestScope(dispatcher)
        translator = Translator(testScope.backgroundScope, InMemoryObjectStore(null))
    }

    @Test
    fun `city names are translated correctly`() = testScope.runTest {
        val zurichServer = createServer(exitCountry = "CH", city = "Zurich")
        val newYorkServer = createServer(exitCountry = "US", city = "New York")

        translator.updateTranslations(
            mapOf(
                "ch" to mapOf("Zurich" to "Zurych"),
                "us" to mapOf("New York" to "Nowy Jork")
            ),
            emptyMap()
        )

        val translations = translator.current
        assertEquals("Zurych", translations.city(CountryId("ch"), "Zurich"))
        assertEquals("Zurych", translations.city(zurichServer))
        assertEquals("Nowy Jork", translations.city(CountryId("us"), "New York"))
        assertEquals("Nowy Jork", translations.city(newYorkServer))
    }

    @Test
    fun `state names are translated correctly`() = testScope.runTest {
        val californiaServer = createServer(exitCountry = "US", state = "California")
        val newYorkServer = createServer(exitCountry = "US", state = "New York")

        translator.updateTranslations(
            cities = emptyMap(),
            states = mapOf(
                "us" to mapOf(
                    "California" to "Kalifornia",
                    "New York" to "Nowy Jork"
                ),
            )
        )

        val translations = translator.current
        assertEquals("Kalifornia", translations.state(CountryId("US"), "California"))
        assertEquals("Kalifornia", translations.state(californiaServer))
        assertEquals("Nowy Jork", translations.state(CountryId("us"), "New York"))
        assertEquals("Nowy Jork", translations.state(newYorkServer))
    }

    @Test
    fun `when no translation is available then original name is returned`() = testScope.runTest {
        val translations = translator.current
        assertEquals("Zurich", translations.city(CountryId("CH"), "Zurich"))
        assertEquals("California", translations.state(CountryId("US"), "California"))
    }

    @Test
    fun `when server list changes then translations are updated`() = testScope.runTest {
        val northCarolinaServer = createServer(exitCountry = "US", state = "North Carolina")
        val zurichServer = createServer(exitCountry = "CH", city = "Zurich")

        translator.updateTranslations(
            cities = mapOf("ch" to mapOf("Zurich" to "Zurych")),
            states = mapOf("us" to mapOf("North Carolina" to "Północna Karolina")),
        )
        assertEquals("Północna Karolina", translator.current.state(northCarolinaServer))
        assertEquals("Zurych", translator.current.city(zurichServer))

        translator.updateTranslations(
            cities = mapOf("ch" to mapOf("Zurich" to "Zúrich")),
            states = mapOf("us" to mapOf("North Carolina" to "Carolina del Norte"),)
        )
        assertEquals("Carolina del Norte", translator.current.state(northCarolinaServer))
        assertEquals("Zúrich", translator.current.city(zurichServer))
    }
}
