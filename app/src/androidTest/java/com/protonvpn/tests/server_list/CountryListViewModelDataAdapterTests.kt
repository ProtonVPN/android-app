/*
 * Copyright (c) 2024. Proton AG
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

package com.protonvpn.tests.server_list

import com.protonvpn.android.models.vpn.SERVER_FEATURE_P2P
import com.protonvpn.android.models.vpn.SERVER_FEATURE_TOR
import com.protonvpn.android.redesign.CityStateId
import com.protonvpn.android.redesign.CountryId
import com.protonvpn.android.redesign.ServerId
import com.protonvpn.android.redesign.countries.ui.CountryListItemData
import com.protonvpn.android.redesign.countries.ui.CountryListViewModelDataAdapter
import com.protonvpn.android.redesign.countries.ui.ServerFilterType
import com.protonvpn.android.redesign.countries.ui.ServerListFilter
import com.protonvpn.android.redesign.vpn.ServerFeature
import com.protonvpn.android.utils.ServerManager
import com.protonvpn.mocks.TestApiConfig
import com.protonvpn.test.shared.createServer
import com.protonvpn.testRules.ProtonHiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject
import kotlin.test.assertEquals

@HiltAndroidTest
class CountryListViewModelDataAdapterTests {

    @get:Rule
    val protonRule = ProtonHiltAndroidRule(this, TestApiConfig.Mocked())

    @Inject
    lateinit var serverManager: ServerManager

    @Inject
    lateinit var adapter: CountryListViewModelDataAdapter

    @Before
    fun setup() {
        protonRule.inject()
    }

    @Test
    fun testCountryFiltering() = runTest {
        serverManager.setServers(
            listOf(
                createServer(exitCountry = "PL", isOnline = false),
                createServer(exitCountry = "PL", isOnline = true),
                createServer(exitCountry = "PL", features = SERVER_FEATURE_TOR),
                createServer(exitCountry = "FR", features = SERVER_FEATURE_P2P or SERVER_FEATURE_TOR),
                createServer(exitCountry = "DE", entryCountry = "SE", isSecureCore = true, tier = 2, isOnline = false),
                createServer(exitCountry = "DE", entryCountry = "IS", isSecureCore = true, tier = 3, isOnline = false),
            ),
            null
        )

        val allCountries = adapter.countries(ServerListFilter()).first()
        val secureCoreCountries = adapter.countries(ServerListFilter(type = ServerFilterType.SecureCore)).first()
        val torCountries = adapter.countries(ServerListFilter(type = ServerFilterType.Tor)).first()
        val p2pCountries = adapter.countries(ServerListFilter(type = ServerFilterType.P2P)).first()

        assertEquals(listOf("PL", "FR"), allCountries.map { it.countryId.countryCode })
        assertEquals(listOf("PL", "FR"), torCountries.map { it.countryId.countryCode })
        assertEquals(listOf("FR"), p2pCountries.map { it.countryId.countryCode })
        assertEquals(
            CountryListItemData.Country(
                countryId = CountryId("DE"),
                inMaintenance = true,
                tier = 2,
                entryCountryId = CountryId.fastest,
            ),
            secureCoreCountries.first()
        )
        assertEquals(
            CountryListItemData.Country(
                countryId = CountryId("PL"),
                inMaintenance = false,
                tier = 0,
                entryCountryId = null,
            ),
            allCountries.first()
        )
    }

    @Test
    fun testCitiesFiltering() = runTest {
        serverManager.setServers(
            listOf(
                createServer(exitCountry = "DE", city = "Berlin"),
                createServer(exitCountry = "PL", city = "Warsaw", translations = mapOf("City" to "Warszawa"), tier = 1),
                createServer(exitCountry = "PL", city = "Warsaw", translations = mapOf("City" to "Warszawa"), tier = 2, isOnline = false),
                createServer(exitCountry = "PL", city = "Cracow", isOnline = false, features = SERVER_FEATURE_TOR),
                createServer(exitCountry = "US", region = "California", city = "Los Angeles"),
                createServer(exitCountry = "US", region = "California", city = "San Francisco"),
                createServer(exitCountry = "US", region = "Alabama", city = "Birmingham"),
            ),
            null
        )

        val plCities = adapter.cities(ServerListFilter(country = CountryId("PL"))).first()
        val torCities = adapter.cities(ServerListFilter(country = CountryId("PL"), type = ServerFilterType.Tor)).first()
        val states = adapter.cities(ServerListFilter(country = CountryId("US"))).first()

        assertEquals(
            listOf(
                CountryListItemData.City(
                    countryId = CountryId("PL"),
                    cityStateId = CityStateId("Warsaw", false),
                    inMaintenance = false,
                    tier = 1,
                    name = "Warszawa"
                ),
                CountryListItemData.City(
                    countryId = CountryId("PL"),
                    cityStateId = CityStateId("Cracow", false),
                    inMaintenance = true,
                    tier = 0,
                    name = "Cracow"
                ),
            ),
            plCities
        )
        assertEquals(listOf("Cracow"), torCities.map { it.cityStateId.name })
        assertEquals(
            listOf(CityStateId("California", true), CityStateId("Alabama", true)),
            states.map { it.cityStateId }
        )
    }

    @Test
    fun testServersFiltering() = runTest {
        serverManager.setServers(
            listOf(
                createServer(exitCountry = "PL", city = "Warsaw", serverName = "PL#1", serverId = "w1", tier = 1),
                createServer(exitCountry = "PL", city = "Warsaw", serverName = "PL#2", serverId = "w2", tier = 2, features = SERVER_FEATURE_TOR),
                createServer(exitCountry = "PL", city = "Cracow", serverName = "PL#3", serverId = "c1", entryCountry = "IS", isSecureCore = true, isOnline = false, load = 70f),
                createServer(exitCountry = "PL", city = "Cracow", serverName = "PL#4", serverId = "c2", hostCountry = "DE"),
                createServer(exitCountry = "US", region = "California", serverName = "US-CA#1", serverId = "cal1"),
                createServer(exitCountry = "US", region = "California", serverName = "US-CA#2", serverId = "cal2"),
            ),
            null
        )

        val plWarsawServers = adapter.servers(ServerListFilter(country = CountryId("PL"), cityStateId = CityStateId("Warsaw", false))).first()
        val plAllCracowServers = adapter.servers(ServerListFilter(country = CountryId("PL"), cityStateId = CityStateId("Cracow", false))).first()
        val plScCracowServers = adapter.servers(ServerListFilter(country = CountryId("PL"), cityStateId = CityStateId("Cracow", false), type = ServerFilterType.SecureCore)).first()
        val californiaServers = adapter.servers(ServerListFilter(country = CountryId("US"), cityStateId = CityStateId("California", true))).first()

        assertEquals(
            listOf(
                CountryListItemData.Server(
                    countryId = CountryId("PL"),
                    serverId = ServerId("w1"),
                    name = "PL#1",
                    loadPercent = 50,
                    serverFeatures = emptySet(),
                    isVirtualLocation = false,
                    inMaintenance = false,
                    tier = 1,
                    entryCountryId = null
                ),
                CountryListItemData.Server(
                    countryId = CountryId("PL"),
                    serverId = ServerId("w2"),
                    name = "PL#2",
                    loadPercent = 50,
                    serverFeatures = setOf(ServerFeature.Tor),
                    isVirtualLocation = false,
                    inMaintenance = false,
                    tier = 2,
                    entryCountryId = null
                ),
            ),
            plWarsawServers
        )
        assertEquals(
            listOf(
                CountryListItemData.Server(
                    countryId = CountryId("PL"),
                    serverId = ServerId("c2"),
                    name = "PL#4",
                    loadPercent = 50,
                    serverFeatures = emptySet(),
                    isVirtualLocation = true,
                    inMaintenance = false,
                    tier = 0,
                    entryCountryId = null
                ),
            ),
            plAllCracowServers
        )
        assertEquals(
            listOf(
                CountryListItemData.Server(
                    countryId = CountryId("PL"),
                    serverId = ServerId("c1"),
                    name = "PL#3",
                    loadPercent = 70,
                    serverFeatures = emptySet(),
                    isVirtualLocation = false,
                    inMaintenance = true,
                    tier = 0,
                    entryCountryId = CountryId("IS")
                ),
                // No secure core server for category All
            ),
            plScCracowServers
        )
        assertEquals(listOf("US-CA#1", "US-CA#2"), californiaServers.map { it.name })
    }

    @Test
    fun testHaveStates() = runTest {
        serverManager.setServers(
            listOf(
                createServer(exitCountry = "PL", city = "Warsaw"),
                createServer(exitCountry = "PL", city = "Cracow"),
                createServer(exitCountry = "US", region = "California"),
                createServer(exitCountry = "US", region = "Alabama"),
            ),
            null
        )
        assertEquals(false, adapter.haveStates(CountryId("PL")))
        assertEquals(true, adapter.haveStates(CountryId("US")))
    }

    @Test
    fun testAvailableTypes() = runTest {
        serverManager.setServers(
            listOf(
                createServer(exitCountry = "PL"),
                createServer(exitCountry = "PL", features = SERVER_FEATURE_TOR),
                createServer(exitCountry = "DE", city = "Berlin", isSecureCore = true),
                createServer(exitCountry = "DE", city = "Hamburg", features = SERVER_FEATURE_P2P),
            ),
            null
        )
        assertEquals(
            setOf(ServerFilterType.All, ServerFilterType.Tor, ServerFilterType.P2P, ServerFilterType.SecureCore),
            adapter.availableTypesFor(null) // All countries
        )
        assertEquals(
            setOf(ServerFilterType.All, ServerFilterType.Tor),
            adapter.availableTypesFor(CountryId("PL"))
        )
        assertEquals(
            setOf(ServerFilterType.All, ServerFilterType.P2P, ServerFilterType.SecureCore),
            adapter.availableTypesFor(CountryId("DE"))
        )
    }

    @Test
    fun testEntryCountries() = runTest {
        serverManager.setServers(
            listOf(
                createServer(exitCountry = "PL"),
                createServer(exitCountry = "PL", entryCountry = "IS", isSecureCore = true),
                createServer(exitCountry = "DE", entryCountry = "IS", isSecureCore = true),
                createServer(exitCountry = "DE", entryCountry = "SE", isSecureCore = true),
            ),
            null
        )

        val plEntries = adapter.entryCountries(CountryId("PL")).first()
        val deEntries = adapter.entryCountries(CountryId("DE")).first()

        assertEquals(
            listOf(
                CountryListItemData.Country(
                    countryId = CountryId("PL"),
                    inMaintenance = false,
                    tier = 0,
                    entryCountryId = CountryId("IS"),
                )
            ),
            plEntries
        )
        assertEquals(
            listOf(
                CountryListItemData.Country(
                    countryId = CountryId("DE"),
                    inMaintenance = false,
                    tier = 0,
                    entryCountryId = CountryId("IS"),
                ),
                CountryListItemData.Country(
                    countryId = CountryId("DE"),
                    inMaintenance = false,
                    tier = 0,
                    entryCountryId = CountryId("SE"),
                )
            ),
            deEntries
        )
    }
}

