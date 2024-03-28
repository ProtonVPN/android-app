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
import com.protonvpn.android.redesign.countries.ui.ServerGroupItemData
import com.protonvpn.android.redesign.countries.ui.ServerListViewModelDataAdapter
import com.protonvpn.android.redesign.countries.ui.ServerFilterType
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
class ServerListViewModelDataAdapterTests {

    @get:Rule
    val protonRule = ProtonHiltAndroidRule(this, TestApiConfig.Mocked())

    @Inject
    lateinit var serverManager: ServerManager

    @Inject
    lateinit var adapter: ServerListViewModelDataAdapter

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
                server(exitCountry = "PL", features = SERVER_FEATURE_TOR),
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
                server(exitCountry = "PL", city = "Cracow", serverName = "PL#3", serverId = "c1", entryCountry = "IS", isSecureCore = true, isOnline = false, load = 70f),
                server(exitCountry = "PL", city = "Cracow", serverName = "PL#4", serverId = "c2", hostCountry = "DE"),
                server(exitCountry = "PL", gatewayName = "gateway", city = "Cracow", serverName = "PL-G#1"), // Gateway server shouldn't show unless gateway filter is set
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
        assertEquals(listOf("PL-G#1"), gatewayServers.map { it.name })
        assertEquals(listOf("gateway"), gatewayServers.map { it.gatewayName })
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
            ),
            null
        )

        val gateways = adapter.gateways().first()

        assertEquals(listOf(
            ServerGroupItemData.Gateway(gatewayName = "gateway1", inMaintenance = true, tier = 2),
            ServerGroupItemData.Gateway(gatewayName = "gateway2", inMaintenance = false, tier = 1),
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
    load: Float = 50f,
    hostCountry: String? = null
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
    load = load,
    hostCountry = hostCountry
)
