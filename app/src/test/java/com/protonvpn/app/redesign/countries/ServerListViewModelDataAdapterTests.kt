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

package com.protonvpn.app.redesign.countries

import com.protonvpn.android.models.vpn.SERVER_FEATURE_P2P
import com.protonvpn.android.models.vpn.SERVER_FEATURE_TOR
import com.protonvpn.android.redesign.CityStateId
import com.protonvpn.android.redesign.CountryId
import com.protonvpn.android.redesign.ServerId
import com.protonvpn.android.redesign.countries.ui.ServerFilterType
import com.protonvpn.android.redesign.countries.ui.ServerGroupItemData
import com.protonvpn.android.redesign.countries.ui.ServerListViewModelDataAdapter
import com.protonvpn.android.redesign.search.ui.SearchViewModelDataAdapter
import com.protonvpn.android.redesign.vpn.ServerFeature
import com.protonvpn.android.utils.ServerManager
import com.protonvpn.test.shared.createServer
import com.protonvpn.app.testRules.RobolectricHiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Locale
import javax.inject.Inject
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@HiltAndroidTest
class ServerListViewModelDataAdapterTests {

    @get:Rule
    val protonRule = RobolectricHiltAndroidRule(this)

    @Inject
    lateinit var serverManager: ServerManager

    @Inject
    lateinit var adapter: ServerListViewModelDataAdapter

    @Inject
    lateinit var searchAdapter: SearchViewModelDataAdapter

    @Before
    fun setup() {
        protonRule.inject()
    }

    @Test
    fun testCountryFiltering() = runTest {
        serverManager.setServers(
            listOf(
                server(exitCountry = "PL", isOnline = false),
                server(exitCountry = "PL", isOnline = true),
                server(exitCountry = "PL", features = SERVER_FEATURE_TOR, isOnline = false),
                server(exitCountry = "FR", features = SERVER_FEATURE_P2P or SERVER_FEATURE_TOR),
                server(exitCountry = "DE", entryCountry = "SE", isSecureCore = true, tier = 2, isOnline = false),
                server(exitCountry = "DE", entryCountry = "IS", isSecureCore = true, tier = 3, isOnline = false),
                server(exitCountry = "JP", gatewayName = "gateway"), // Country with just gateways shouldn't show for countries
            ),
            null
        )

        val allCountries = adapter.countries().first()
        val secureCoreCountries = adapter.countries(ServerFilterType.SecureCore).first()
        val torCountries = adapter.countries(ServerFilterType.Tor).first()
        val p2pCountries = adapter.countries(ServerFilterType.P2P).first()

        assertEquals(listOf("PL", "FR"), allCountries.map { it.countryId.countryCode })
        assertEquals(listOf("PL", "FR"), torCountries.map { it.countryId.countryCode })
        assertTrue(torCountries.find { it.countryId == CountryId("PL") }?.inMaintenance == true)
        assertEquals(listOf("FR"), p2pCountries.map { it.countryId.countryCode })
        assertEquals(
            ServerGroupItemData.Country(
                countryId = CountryId("DE"),
                inMaintenance = true,
                tier = 2,
                entryCountryId = CountryId.fastest,
            ),
            secureCoreCountries.first()
        )
        assertEquals(
            ServerGroupItemData.Country(
                countryId = CountryId("PL"),
                inMaintenance = false,
                tier = 1,
                entryCountryId = null,
            ),
            allCountries.first()
        )
    }

    @Test
    fun testCitiesFiltering() = runTest {
        serverManager.setServers(
            listOf(
                server(exitCountry = "DE", city = "Berlin"),
                server(exitCountry = "PL", city = "Warsaw", translations = mapOf("City" to "Warszawa"), tier = 2),
                server(exitCountry = "PL", city = "Warsaw", translations = mapOf("City" to "Warszawa"), tier = 3, isOnline = false),
                server(exitCountry = "PL", city = "Cracow", isOnline = false, features = SERVER_FEATURE_TOR),
                server(exitCountry = "PL", city = "Wroclaw", gatewayName = "gateway"), // City with only gateways shouldn't show
                server(exitCountry = "US", state = "California", city = "Los Angeles"),
                server(exitCountry = "US", state = "California", city = "San Francisco"),
                server(exitCountry = "US", state = "Alabama", city = "Birmingham"),
            ),
            null
        )

        val plCities = adapter.cities(country = CountryId("PL")).first()
        val torCities = adapter.cities(country = CountryId("PL"), filter = ServerFilterType.Tor).first()
        val states = adapter.cities(country = CountryId("US")).first()

        assertEquals(
            listOf(
                ServerGroupItemData.City(
                    countryId = CountryId("PL"),
                    cityStateId = CityStateId("Warsaw", false),
                    inMaintenance = false,
                    tier = 2,
                    name = "Warszawa"
                ),
                ServerGroupItemData.City(
                    countryId = CountryId("PL"),
                    cityStateId = CityStateId("Cracow", false),
                    inMaintenance = true,
                    tier = 1,
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
                server(exitCountry = "PL", city = "Warsaw", serverName = "PL#1", serverId = "w1", tier = 2),
                server(exitCountry = "PL", city = "Warsaw", serverName = "PL#2", serverId = "w2", tier = 3, features = SERVER_FEATURE_TOR),
                server(exitCountry = "PL", city = "Cracow", serverName = "PL#3", serverId = "c1", entryCountry = "IS", isSecureCore = true, isOnline = false, loadPercent = 70f),
                server(exitCountry = "PL", city = "Cracow", serverName = "PL#4", serverId = "c2", hostCountry = "DE"),
                server(exitCountry = "PL", gatewayName = "gateway", city = "Cracow", serverName = "PL-G#1"), // Gateway server shouldn't show unless gateway filter is set
                server(exitCountry = "PL", gatewayName = "gateway", city = "Cracow", serverName = "PL-G#2", tier = 0), // Gateway server shouldn't show unless gateway filter is set
                server(exitCountry = "US", state = "California", serverName = "US-CA#1", serverId = "cal1"),
                server(exitCountry = "US", state = "California", serverName = "US-CA#2", serverId = "cal2"),
            ),
            null
        )

        val plWarsawServers = adapter.servers(country = CountryId("PL"), cityStateId = CityStateId("Warsaw", false)).first()
        val plAllCracowServers = adapter.servers(country = CountryId("PL"), cityStateId = CityStateId("Cracow", false)).first()
        val plScCracowServers = adapter.servers(country = CountryId("PL"), cityStateId = CityStateId("Cracow", false), filter = ServerFilterType.SecureCore).first()
        val californiaServers = adapter.servers(country = CountryId("US"), cityStateId = CityStateId("California", true)).first()
        val gatewayServers = adapter.servers(gatewayName = "gateway").first()

        assertEquals(
            listOf(
                ServerGroupItemData.Server(
                    countryId = CountryId("PL"),
                    serverId = ServerId("w1"),
                    name = "PL#1",
                    loadPercent = 50,
                    serverFeatures = emptySet(),
                    isVirtualLocation = false,
                    inMaintenance = false,
                    tier = 2,
                    entryCountryId = null,
                    gatewayName = null
                ),
                ServerGroupItemData.Server(
                    countryId = CountryId("PL"),
                    serverId = ServerId("w2"),
                    name = "PL#2",
                    loadPercent = 50,
                    serverFeatures = setOf(ServerFeature.Tor),
                    isVirtualLocation = false,
                    inMaintenance = false,
                    tier = 3,
                    entryCountryId = null,
                    gatewayName = null
                ),
            ),
            plWarsawServers
        )
        assertEquals(
            listOf(
                ServerGroupItemData.Server(
                    countryId = CountryId("PL"),
                    serverId = ServerId("c2"),
                    name = "PL#4",
                    loadPercent = 50,
                    serverFeatures = emptySet(),
                    isVirtualLocation = true,
                    inMaintenance = false,
                    tier = 1,
                    entryCountryId = null,
                    gatewayName = null
                ),
            ),
            plAllCracowServers
        )
        assertEquals(
            listOf(
                ServerGroupItemData.Server(
                    countryId = CountryId("PL"),
                    serverId = ServerId("c1"),
                    name = "PL#3",
                    loadPercent = 70,
                    serverFeatures = emptySet(),
                    isVirtualLocation = false,
                    inMaintenance = true,
                    tier = 1,
                    entryCountryId = CountryId("IS"),
                    gatewayName = null
                ),
                // No secure core server for category All
            ),
            plScCracowServers
        )
        assertEquals(listOf("US-CA#1", "US-CA#2"), californiaServers.map { it.name })
        assertEquals(listOf("PL-G#1", "PL-G#2"), gatewayServers.map { it.name })
        assertEquals(listOf("gateway", "gateway"), gatewayServers.map { it.gatewayName })
    }

    @Test
    fun testHaveStates() = runTest {
        serverManager.setServers(
            listOf(
                server(exitCountry = "PL", city = "Warsaw"),
                server(exitCountry = "PL", city = "Cracow"),
                server(exitCountry = "US", state = "California"),
                server(exitCountry = "US", state = "Alabama"),
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
                server(exitCountry = "PL"),
                server(exitCountry = "PL", features = SERVER_FEATURE_TOR),
                server(exitCountry = "DE", city = "Berlin", isSecureCore = true),
                server(exitCountry = "DE", city = "Hamburg", features = SERVER_FEATURE_P2P),
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
                server(exitCountry = "PL"),
                server(exitCountry = "PL", entryCountry = "IS", isSecureCore = true),
                server(exitCountry = "DE", entryCountry = "IS", isSecureCore = true),
                server(exitCountry = "DE", entryCountry = "SE", isSecureCore = true),
            ),
            null
        )

        val plEntries = adapter.entryCountries(CountryId("PL")).first()
        val deEntries = adapter.entryCountries(CountryId("DE")).first()

        assertEquals(
            listOf(
                ServerGroupItemData.Country(
                    countryId = CountryId("PL"),
                    inMaintenance = false,
                    tier = 1,
                    entryCountryId = CountryId("IS"),
                )
            ),
            plEntries
        )
        assertEquals(
            listOf(
                ServerGroupItemData.Country(
                    countryId = CountryId("DE"),
                    inMaintenance = false,
                    tier = 1,
                    entryCountryId = CountryId("IS"),
                ),
                ServerGroupItemData.Country(
                    countryId = CountryId("DE"),
                    inMaintenance = false,
                    tier = 1,
                    entryCountryId = CountryId("SE"),
                )
            ),
            deEntries
        )
    }

    @Test
    fun testGateways() = runTest {
        serverManager.setServers(
            listOf(
                server(exitCountry = "US"),
                server(exitCountry = "US", gatewayName = "gateway1", isOnline = false, tier = 2),
                server(exitCountry = "JP", gatewayName = "gateway2"),
                server(exitCountry = "JP", gatewayName = "gateway2"),
                // Free servers in gateways should be displayed.
                server(exitCountry = "NL", gatewayName = "gateway3", tier = 0),
            ),
            null
        )

        val gateways = adapter.gateways().first()

        assertEquals(listOf(
            ServerGroupItemData.Gateway(gatewayName = "gateway1", inMaintenance = true, tier = 2),
            ServerGroupItemData.Gateway(gatewayName = "gateway2", inMaintenance = false, tier = 1),
            ServerGroupItemData.Gateway(gatewayName = "gateway3", inMaintenance = false, tier = 0),
        ), gateways)
    }

    @Test
    fun freeServersNotListed() = runTest {
        serverManager.setServers(
            listOf(
                server(exitCountry = "PL", tier = 0),
                server(exitCountry = "PL", tier = 1),
            ),
            null
        )
        val servers = adapter.servers(country = CountryId("PL")).first()
        assertEquals(1, servers.size)
    }

    private suspend fun searchTestSetup() {
        serverManager.setServers(
            listOf(
                server(exitCountry = "PL", city = "Warsaw", serverName = "PL#1"),
                server(exitCountry = "PL", city = "Warsaw", serverName = "PL#2", features = SERVER_FEATURE_TOR),
                server(exitCountry = "PL", city = "Cracow", serverName = "PL#3", isSecureCore = true, translations = mapOf("City" to "Kraków")),
                server(exitCountry = "PL", city = "Cracow", serverName = "PL#4", translations = mapOf("City" to "Kraków")),
                server(exitCountry = "US", state = "California", serverName = "US-CA#1"),
                server(exitCountry = "US", city = "New York", serverName = "US-NY#2"),
            ),
            null
        )
    }

    @Test
    fun basicSearchTest() = runTest {
        searchTestSetup()

        val result = searchAdapter.search("p", Locale("PL")).first()
        assertEquals(
            listOf("PL#1", "PL#2", "PL#4"), // All PL servers without secure core
            result[ServerFilterType.All]?.servers?.map { it.name }
        )
        assertEquals(
            listOf("PL"),
            result[ServerFilterType.All]?.countries?.map { it.countryId.countryCode }
        )
        assertEquals(0, result[ServerFilterType.All]?.cities?.size)
    }

    @Test
    fun searchFiltersTest() = runTest {
        searchTestSetup()
        val result = searchAdapter.search("p", Locale("PL")).first()
        assertEquals(
            listOf("PL#3"),
            result[ServerFilterType.SecureCore]?.servers?.map { it.name }
        )
        assertEquals(
            listOf("PL#2"),
            result[ServerFilterType.Tor]?.servers?.map { it.name }
        )
    }

    @Test
    fun accentAndCaseSearchTest() = runTest {
        searchTestSetup()
        val result = searchAdapter.search("krak", Locale("PL")).first()
        assertEquals(
            listOf("Kraków"),
            result[ServerFilterType.All]?.cities?.map { it.textMatch?.fullText }
        )
    }

    @Test
    fun testFallbackToEnglish() = runTest {
        searchTestSetup()
        val result = searchAdapter.search("crac", Locale("PL")).first()
        assertEquals(
            listOf("Cracow"),
            result[ServerFilterType.All]?.cities?.map { it.textMatch?.fullText }
        )
    }

    @Test
    fun testSearchStates() = runTest {
        searchTestSetup()
        val result = searchAdapter.search("cal", Locale.US).first()
        assertEquals(
            listOf("California"),
            result[ServerFilterType.All]?.states?.map { it.textMatch?.fullText }
        )
    }

    @Test
    fun testSearchByServerNumber() = runTest {
        searchTestSetup()
        val result = searchAdapter.search("1", Locale.US).first()
        assertEquals(
            listOf("PL#1", "US-CA#1"),
            result[ServerFilterType.All]?.servers?.map { it.name }
        )
    }

    @Test
    fun testSearchByNotFirstWord() = runTest {
        searchTestSetup()
        val result = searchAdapter.search("york", Locale.US).first()
        assertEquals(
            listOf("New York"),
            result[ServerFilterType.All]?.cities?.map { it.textMatch?.fullText }
        )

        // We match only beginning of words
        val searchMiddleOfTheWord = searchAdapter.search("ork", Locale.US).first()
        assertEquals(0, searchMiddleOfTheWord[ServerFilterType.All]?.cities?.size)
    }

    @Test
    fun testGetHostCountry() = runTest {
        serverManager.setServers(
            listOf(
                server(exitCountry = "PL", city = "Warsaw", hostCountry = "US"),
                server(exitCountry = "US", city = "New York"),
            ),
            null
        )

        assertEquals(CountryId("US"), adapter.getHostCountry(CountryId("PL")))
        assertEquals(null, adapter.getHostCountry(CountryId("US")))
    }
}

fun server(
    exitCountry: String,
    city: String? = null,
    state: String? = null,
    serverName: String = "serverName",
    serverId: String = "ID",
    entryCountry: String = exitCountry,
    tier: Int = 1, // Servers with lower tier shouldn't be shown from adapter
    features: Int = 0,
    gatewayName: String? = null,
    translations: Map<String, String?>? = null,
    isSecureCore: Boolean = false,
    isOnline: Boolean = true,
    loadPercent: Float = 50f,
    hostCountry: String? = ""
) = createServer(
    exitCountry = exitCountry,
    city = city,
    state = state,
    serverName = serverName,
    serverId = serverId,
    entryCountry = entryCountry,
    tier = tier,
    features = features,
    gatewayName = gatewayName,
    translations = translations,
    isSecureCore = isSecureCore,
    isOnline = isOnline,
    loadPercent = loadPercent,
    hostCountry = hostCountry
)
