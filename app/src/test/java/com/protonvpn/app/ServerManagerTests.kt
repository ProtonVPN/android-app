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

package com.protonvpn.app

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.protonvpn.android.auth.data.VpnUser
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.models.config.TransmissionProtocol
import com.protonvpn.android.models.config.UserData
import com.protonvpn.android.models.config.VpnProtocol
import com.protonvpn.android.models.profiles.Profile
import com.protonvpn.android.models.profiles.ProfileColor
import com.protonvpn.android.models.profiles.ServerWrapper
import com.protonvpn.android.models.vpn.SERVER_FEATURE_RESTRICTED
import com.protonvpn.android.models.vpn.Server
import com.protonvpn.android.models.vpn.usecase.SupportsProtocol
import com.protonvpn.android.utils.CountryTools
import com.protonvpn.android.utils.ServerManager
import com.protonvpn.android.utils.Storage
import com.protonvpn.android.vpn.ProtocolSelection
import com.protonvpn.test.shared.MockSharedPreference
import com.protonvpn.test.shared.createGetSmartProtocols
import com.protonvpn.test.shared.createInMemoryServersStore
import com.protonvpn.test.shared.createServer
import com.protonvpn.test.shared.mockVpnUser
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.mockk
import io.mockk.mockkObject
import kotlinx.serialization.builtins.ListSerializer
import me.proton.core.util.kotlin.deserialize
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.Locale
import java.util.UUID

class ServerManagerTests {

    private lateinit var manager: ServerManager

    @RelaxedMockK private lateinit var currentUser: CurrentUser
    @RelaxedMockK private lateinit var vpnUser: VpnUser

    private lateinit var userData: UserData

    private val gatewayServer = createServer(
        "dedicated",
        serverName = "CA Gateway#1",
        exitCountry = "CA",
        gatewayName = "Gateway 1",
        features = SERVER_FEATURE_RESTRICTED
    )
    private lateinit var regularServers: List<Server>

    @get:Rule
    var rule = InstantTaskExecutorRule()

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        Storage.setPreferences(MockSharedPreference())
        userData = UserData.create()
        mockkObject(CountryTools)
        currentUser.mockVpnUser { vpnUser }
        every { vpnUser.userTier } returns 2
        every { CountryTools.getPreferredLocale() } returns Locale.US

        val supportsProtocol = SupportsProtocol(createGetSmartProtocols())
        manager = ServerManager(userData, currentUser, { 0L }, supportsProtocol, createInMemoryServersStore(), mockk(relaxed = true))
        val serversFile = File(javaClass.getResource("/Servers.json")?.path)
        regularServers = serversFile.readText().deserialize(ListSerializer(Server.serializer()))

        val allServers = regularServers +  gatewayServer
        manager.setServers(allServers, null)
    }

    @Test
    fun doNotChooseOfflineServerFromCountry() {
        val country = manager.getVpnExitCountry("CA", false)
        val countryBestServer = manager.getBestScoreServer(country!!)
        Assert.assertEquals("CA#2", countryBestServer!!.serverName)
    }

    @Test
    fun doNotChooseOfflineServerFromAll() {
        Assert.assertEquals(
            "DE#1", manager.getBestScoreServer(userData.secureCoreEnabled)!!.serverName
        )
    }

    @Test
    fun testFilterForProtocol() {
        userData.protocol = ProtocolSelection(VpnProtocol.WireGuard, TransmissionProtocol.TCP)
        val filtered = manager.filterCountriesForProtocol(manager.getVpnCountries())
        Assert.assertEquals(listOf("CA#1", "DE#1"), filtered.flatMap { it.serverList.map { it.serverName } })
        val canada = filtered.first { it.flag == "CA" }
        Assert.assertEquals(1, canada.serverList.size)
        Assert.assertEquals(1, canada.serverList.first().connectingDomains.size)
    }

    @Test
    fun testOnlineAccessibleServersSeparatesGatewaysFromRegular() {
        val gatewayName = gatewayServer.gatewayName
        val gatewayServers = manager.getOnlineAccessibleServers(false, gatewayName, vpnUser, ProtocolSelection.SMART)
        val regularServers = manager.getOnlineAccessibleServers(false, null, vpnUser, ProtocolSelection.SMART)

        assertEquals(listOf(gatewayServer), gatewayServers)
        assertFalse(regularServers.contains(gatewayServer))
    }

    @Test
    fun testGetServerById() {
        val server = manager.getServerById(
            "1H8EGg3J1QpSDL6K8hGsTvwmHXdtQvnxplUMePE7Hruen5JsRXvaQ75-sXptu03f0TCO-he3ymk0uhrHx6nnGQ=="
        )
        Assert.assertNotNull(server)
        Assert.assertEquals("CA#2", server?.serverName)
    }

    @Test
    fun `deleting default profile clears UserData defaultProfileId`() {
        val profile = Profile(
            "test",
            null,
            ServerWrapper.makeFastestForCountry("pl"),
            ProfileColor.OLIVE.id,
            false,
            "WireGuard",
            null
        )

        userData.defaultProfileId = profile.id
        manager.deleteProfile(profile)

        assertNull(userData.defaultProfileId)
    }

    @Test
    fun `when defaultProfileId is invalid then defaultConnection falls back to saved profiles`() {
        userData.defaultProfileId = UUID.randomUUID()
        val profile = manager.defaultConnection
        assertEquals(manager.getSavedProfiles().first(), profile)
    }
}
