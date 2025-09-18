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

package com.protonvpn.app.profiles

import android.net.Uri
import com.protonvpn.android.models.config.TransmissionProtocol
import com.protonvpn.android.models.config.VpnProtocol
import com.protonvpn.android.profiles.data.ProfileAutoOpen
import com.protonvpn.android.profiles.data.toProfile
import com.protonvpn.android.profiles.ui.SettingsScreenState
import com.protonvpn.android.profiles.ui.TypeAndLocationScreenState
import com.protonvpn.android.profiles.usecases.PrivateBrowsingAvailability
import com.protonvpn.android.redesign.CityStateId
import com.protonvpn.android.redesign.CountryId
import com.protonvpn.android.redesign.settings.ui.NatType
import com.protonvpn.android.redesign.vpn.ConnectIntent
import com.protonvpn.android.settings.data.CustomDnsSettings
import com.protonvpn.android.telemetry.ProfilesTelemetry
import com.protonvpn.android.telemetry.TelemetryFlowHelper
import com.protonvpn.android.vpn.ProtocolSelection
import com.protonvpn.mocks.FakeCommonDimensions
import com.protonvpn.mocks.TestTelemetryReporter
import com.protonvpn.test.shared.createProfileEntity
import io.mockk.MockKAnnotations
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProfilesTelemetryTests {

    private lateinit var testScope: TestScope
    private lateinit var testTelemetry: TestTelemetryReporter

    private val commonDimensions = FakeCommonDimensions(mapOf("user_tier" to "paid"))
    private val settingsScreenState = SettingsScreenState(
        netShield = true,
        protocol = ProtocolSelection(VpnProtocol.WireGuard),
        natType = NatType.Moderate,
        lanConnections = true,
        lanConnectionsAllowDirect = false,
        autoOpen = ProfileAutoOpen.None,
        customDnsSettings = CustomDnsSettings(false),
        isAutoOpenNew = true,
        isPrivateDnsActive = false,
        showPrivateBrowsing = true
    )

    private lateinit var profilesTelemetry: ProfilesTelemetry

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        testScope = TestScope()
        testTelemetry = TestTelemetryReporter()

        profilesTelemetry = ProfilesTelemetry(
            commonDimensions,
            TelemetryFlowHelper(testScope.backgroundScope, testTelemetry)
        )
    }

    @Test
    fun `profile create`() = testScope.runTest {
        val typeAndLocation = createStandardTypeAndLocation(CountryId.sweden, null, null)
        profilesTelemetry.profileCreated(typeAndLocation, settingsScreenState, 5, PrivateBrowsingAvailability.AvailableWithDefault)
        runCurrent()

        assertEquals(1, testTelemetry.collectedEvents.size)
        val event = testTelemetry.collectedEvents.first()
        assertEquals("profile_created", event.eventName)
        assertEquals("5-9", event.dimensions["profile_count"])
        assertEquals("specific", event.dimensions["country_selection_type"])
    }

    @Test
    fun `profile update`() = testScope.runTest {
        val typeAndLocation = createStandardTypeAndLocation(CountryId.sweden, null, null)
        val profile = createProfileEntity(
            isUserCreated = true,
            connectIntent = ConnectIntent.Fastest
        ).toProfile()
        profilesTelemetry.profileUpdated(typeAndLocation, settingsScreenState, profile, false, PrivateBrowsingAvailability.AvailableWithDefault)
        runCurrent()
        assertEquals(1, testTelemetry.collectedEvents.size)
        val event = testTelemetry.collectedEvents.first()
        assertEquals("profile_updated", event.eventName)
        assertEquals("standard", event.dimensions["vpn_connection_type"])
        assertEquals("user_created", event.dimensions["profile_type"])
        assertEquals("normal_route", event.dimensions["edit_route_source"])
    }

    @Test
    fun `profile delete`() = testScope.runTest {
        val profile = createProfileEntity(
            isUserCreated = false,
            connectIntent = ConnectIntent.Fastest
        ).toProfile()
        profilesTelemetry.profileDeleted(profile, 10)
        runCurrent()
        assertEquals(1, testTelemetry.collectedEvents.size)
        val event = testTelemetry.collectedEvents.first()
        assertEquals("profile_deleted", event.eventName)
        assertEquals("10-49", event.dimensions["profile_count"])
        assertEquals("pre_made", event.dimensions["profile_type"])
    }

    @Test
    fun `profile count dimension`() = testScope.runTest {
        val typeAndLocation = createStandardTypeAndLocation(CountryId.sweden, null, null)
        listOf(
            1 to "1",
            2 to "2",
            3 to "3",
            4 to "4",
            5 to "5-9",
            6 to "5-9",
            9 to "5-9",
            10 to "10-49",
            11 to "10-49",
            49 to "10-49",
            50 to ">=50",
            1000 to ">=50"
        ).forEach { (count, expected) ->
            profilesTelemetry.profileCreated(typeAndLocation, settingsScreenState, profileCount = count, PrivateBrowsingAvailability.AvailableWithDefault)
            runCurrent()
            val countDimension = testTelemetry.collectedEvents.last().dimensions["profile_count"]
            assertEquals("Incorrect dimension for profile count $count", expected, countDimension)
        }
    }

    @Test
    fun `standard selection types`() = testScope.runTest {
        val dimensionsFastest = reportEventAndGetDimensions(
            createStandardTypeAndLocation(CountryId.fastest, null, null)
        )
        assertConnectionDimensions(
            dimensionsFastest,
            type = "standard",
            expectedCountry = "fastest",
        )

        val dimensionsFastestExcludingMy = reportEventAndGetDimensions(
            createStandardTypeAndLocation(CountryId.fastestExcludingMyCountry, null, null)
        )
        assertConnectionDimensions(
            dimensionsFastestExcludingMy,
            type = "standard",
            expectedCountry = "fastest_excluding_mine",
        )

        val dimensionsFastestCity = reportEventAndGetDimensions(
            createStandardTypeAndLocation(CountryId.iceland, CityStateId.fastestCity, null)
        )
        assertConnectionDimensions(
            dimensionsFastestCity,
            type = "standard",
            expectedCountry = "specific",
            expectedCityState = "fastest"
        )

        val dimensionsFastestState = reportEventAndGetDimensions(
            createStandardTypeAndLocation(CountryId.iceland, CityStateId.fastestState, null)
        )
        assertConnectionDimensions(
            dimensionsFastestState,
            type = "standard",
            expectedCountry = "specific",
            expectedCityState = "fastest"
        )

        val cityReykjavik = CityStateId("Reykjavik", false)
        val serverItemFastest = TypeAndLocationScreenState.ServerItem.fastest(true)
        val dimensionsFastestServer = reportEventAndGetDimensions(
            createStandardTypeAndLocation(CountryId.iceland, cityReykjavik, serverItemFastest)
        )
        assertConnectionDimensions(
            dimensionsFastestServer,
            type = "standard",
            expectedCountry = "specific",
            expectedCityState = "specific",
            expectedServer = "fastest"
        )

        val serverItemSpecific = TypeAndLocationScreenState.ServerItem("Server A", "Server ID", CountryId.iceland, true)
        val dimensionsSpecificServer = reportEventAndGetDimensions(
            createStandardTypeAndLocation(CountryId.iceland, cityReykjavik,  serverItemSpecific)
        )
        assertConnectionDimensions(
            dimensionsSpecificServer,
            type = "standard",
            expectedCountry = "specific",
            expectedCityState = "specific",
            expectedServer = "specific"
        )
    }

    @Test
    fun `secure core selection types`() = testScope.runTest {
        val dimensionsFastest = reportEventAndGetDimensions(
            createSecureCoreTypeAndLocation(CountryId.fastest, null)
        )
        assertConnectionDimensions(dimensionsFastest, type = "secure_core", expectedCountry = "fastest")

        val dimensionsFastestExcludingMy = reportEventAndGetDimensions(
            createSecureCoreTypeAndLocation(CountryId.fastestExcludingMyCountry, null)
        )
        assertConnectionDimensions(dimensionsFastestExcludingMy, type = "secure_core", expectedCountry = "fastest_excluding_mine")

        val dimensionsSwedenFastest = reportEventAndGetDimensions(
            createSecureCoreTypeAndLocation(CountryId.fastest, CountryId.fastest)
        )
        assertConnectionDimensions(
            dimensionsSwedenFastest,
            type = "secure_core",
            expectedCountry = "fastest",
            expectedEntryCountry = "fastest"
        )

        val dimensionsSwedenIceland = reportEventAndGetDimensions(
            createSecureCoreTypeAndLocation(CountryId.sweden, CountryId.iceland)
        )
        assertConnectionDimensions(
            dimensionsSwedenIceland,
            type = "secure_core",
            expectedCountry = "specific",
            expectedEntryCountry = "specific"
        )
    }

    @Test
    fun `gateway selection types`() = testScope.runTest {
        val dimensionsFastestServer = reportEventAndGetDimensions(
            createGatewayTypeAndLocation(TypeAndLocationScreenState.ServerItem.fastest(false))
        )
        assertEquals("gateway", dimensionsFastestServer["vpn_connection_type"])
        assertEquals("specific", dimensionsFastestServer["gateway_selection_type"])

        val dimensionsSpecificServer = reportEventAndGetDimensions(
            createGatewayTypeAndLocation(TypeAndLocationScreenState.ServerItem("Server 1", "Server ID", null, true))
        )
        assertEquals("gateway", dimensionsSpecificServer["vpn_connection_type"])
        assertEquals("specific", dimensionsSpecificServer["gateway_selection_type"])
        assertEquals("specific", dimensionsSpecificServer["server_selection_type"])
    }

    @Test
    fun `profile settings dimensions`() = testScope.runTest {
        val url = mockk<Uri>()
        val typeAndLocation = createSecureCoreTypeAndLocation(CountryId.fastest, null)
        val settingsDimensions1 = reportEventAndGetDimensions(
            typeAndLocation,
            settings = SettingsScreenState(
                netShield = false,
                protocol = ProtocolSelection.SMART,
                natType = NatType.Moderate,
                lanConnections = false,
                lanConnectionsAllowDirect = false,
                autoOpen = ProfileAutoOpen.Url(url, openInPrivateMode = true),
                customDnsSettings = CustomDnsSettings(false),
                isAutoOpenNew = true,
                isPrivateDnsActive = false,
                showPrivateBrowsing = true
            ),
        )
        assertEquals("off", settingsDimensions1["netshield_setting"])
        assertEquals("smart", settingsDimensions1["vpn_protocol"])
        assertEquals("type2_moderate", settingsDimensions1["nat_type"])
        assertEquals("off", settingsDimensions1["lan_access"])
        assertEquals("url_private", settingsDimensions1["auto_open"])

        val settingsDimensions2 = reportEventAndGetDimensions(
            typeAndLocation,
            SettingsScreenState(
                netShield = true,
                protocol = ProtocolSelection(VpnProtocol.WireGuard, TransmissionProtocol.TLS),
                natType = NatType.Strict,
                lanConnections = true,
                lanConnectionsAllowDirect = false,
                autoOpen = ProfileAutoOpen.None,
                customDnsSettings = CustomDnsSettings(false),
                isAutoOpenNew = true,
                isPrivateDnsActive = false,
                showPrivateBrowsing = true
            )
        )
        assertEquals("f2", settingsDimensions2["netshield_setting"])
        assertEquals("wireguard_tls", settingsDimensions2["vpn_protocol"])
        assertEquals("type3_strict", settingsDimensions2["nat_type"])
        assertEquals("on", settingsDimensions2["lan_access"])
    }

    @Test
    fun `private browsing availability sent only when auto open in private enabled`() = testScope.runTest {
        val url = mockk<Uri>()
        val typeAndLocation = createStandardTypeAndLocation(CountryId.sweden, null, null)

        fun checkAvailabilityForAutoOpen(expectedAvailability: String,value: ProfileAutoOpen) {
            val dimensions = reportEventAndGetDimensions(
                typeAndLocation,
                settings = settingsScreenState.copy(autoOpen = value)
            )
            assertEquals(expectedAvailability, dimensions["auto_open_private_mode_availability"])
        }

        checkAvailabilityForAutoOpen("n/a", ProfileAutoOpen.None)
        checkAvailabilityForAutoOpen("n/a", ProfileAutoOpen.App("com.example.app"))
        checkAvailabilityForAutoOpen("n/a", ProfileAutoOpen.Url(url, openInPrivateMode = false))
        checkAvailabilityForAutoOpen("available_with_default", ProfileAutoOpen.Url(url, openInPrivateMode = true))
    }

    private fun TestScope.reportEventAndGetDimensions(
        typeAndLocation: TypeAndLocationScreenState,
        settings: SettingsScreenState = settingsScreenState
    ): Map<String, String> {
        profilesTelemetry.profileCreated(typeAndLocation, settings, profileCount = 1, PrivateBrowsingAvailability.AvailableWithDefault)
        runCurrent()
        return testTelemetry.collectedEvents.last().dimensions
    }

    private fun createStandardTypeAndLocation(
        country: CountryId,
        cityOrState: CityStateId?,
        serverItem: TypeAndLocationScreenState.ServerItem? = null
    ) = TypeAndLocationScreenState.Standard(
        country = TypeAndLocationScreenState.CountryItem(country, online = true),
        cityOrState = cityOrState?.let { TypeAndLocationScreenState.CityOrStateItem(null, it, true) },
        server = serverItem,
        availableTypes = emptyList(),
        selectableServers = emptyList(),
        selectableCountries = emptyList(),
        selectableCitiesOrStates = emptyList()
    )

    private fun createSecureCoreTypeAndLocation(exitCountry: CountryId, entryCountry: CountryId?) =
        TypeAndLocationScreenState.SecureCore(
            exitCountry = TypeAndLocationScreenState.CountryItem(exitCountry, online = true),
            entryCountry = entryCountry?.let { TypeAndLocationScreenState.CountryItem(entryCountry, online = true) },
            selectableEntryCountries = emptyList(),
            selectableExitCountries = emptyList(),
            availableTypes = emptyList()
        )

    private fun createGatewayTypeAndLocation(serverItem: TypeAndLocationScreenState.ServerItem) =
        TypeAndLocationScreenState.Gateway(
            gateway = TypeAndLocationScreenState.GatewayItem("gateway", true),
            server = serverItem,
            availableTypes = emptyList(),
            selectableServers = emptyList(),
            selectableGateways = emptyList(),
        )

    private fun assertConnectionDimensions(
        dimensions: Map<String, String>,
        type: String,
        expectedCountry: String = "n/a",
        expectedEntryCountry: String = "n/a",
        expectedCityState: String = "n/a",
        expectedServer: String = "n/a",
        expectedGateway: String = "n/a",
    ) {
        assertEquals(type, dimensions["vpn_connection_type"])
        assertEquals(expectedCountry, dimensions["country_selection_type"])
        assertEquals(expectedEntryCountry, dimensions["entry_country_selection_type"])
        assertEquals(expectedCityState, dimensions["city_or_state_selection_type"])
        assertEquals(expectedServer, dimensions["server_selection_type"])
        assertEquals(expectedGateway, dimensions["gateway_selection_type"])
    }
}
