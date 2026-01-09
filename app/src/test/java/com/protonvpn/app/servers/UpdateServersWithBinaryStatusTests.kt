/*
 * Copyright (c) 2026. Proton AG
 *
 *  This file is part of ProtonVPN.
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

package com.protonvpn.app.servers

import com.protonvpn.android.appconfig.UserCountryIpBased
import com.protonvpn.android.servers.Server
import com.protonvpn.android.servers.UpdateServersWithBinaryStatusImpl
import com.protonvpn.android.servers.api.ServerStatusReference
import com.protonvpn.android.ui.home.ServerListUpdaterPrefs
import com.protonvpn.test.shared.MockSharedPreferencesProvider
import com.protonvpn.test.shared.createServer
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import junit.framework.TestCase.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Base64
import kotlin.test.assertNotNull

class UpdateServersWithBinaryStatusTests {

    @MockK
    private lateinit var mockUserCountry: UserCountryIpBased

    private lateinit var updater: UpdateServersWithBinaryStatusImpl

    @Before
    fun setup() {
        MockKAnnotations.init(this)

        every { mockUserCountry.invoke() } returns null

        updater = UpdateServersWithBinaryStatusImpl(
            ServerListUpdaterPrefs(MockSharedPreferencesProvider()),
            mockUserCountry
        )
    }

    @Test
    fun `server loads are updated for correct servers`() {
        val serversToUpdate = listOf(
            // Order of servers different than status indices to check that it's not simply the
            // order in the list that is being used.
            createServer(
                serverId = "server2",
                loadPercent = 20f,
                statusReference = ServerStatusReference(index = 1u, penalty = 0.0, cost = 0)
            ),
            createServer(
                serverId = "server1",
                loadPercent = 10f,
                statusReference = ServerStatusReference(index = 0u, penalty = 0.0, cost = 0)
            )
        )

        // Index 0: enabled, load 50. Index 1: disabled, load 75
        val updatedServers = applyUpdate(serversToUpdate, "AQAAAAMyAAAAAAJLAACAPw==")
        assertEquals(50f, updatedServers["server1"]!!.load, 0.1f)
        assertTrue(updatedServers["server1"]!!.online)
        assertEquals(75f, updatedServers["server2"]!!.load, 0.1f)
        assertFalse(updatedServers["server2"]!!.online)
    }

    @Test
    fun `servers not in status file are hidden`() {
        val serversToUpdate = listOf(
            // Order of servers different than status indices to check that it's not simply the
            // order in the list that is being used.
            createServer(
                serverId = "server2",
                loadPercent = 20f,
                statusReference = ServerStatusReference(index = 1u, penalty = 0.0, cost = 0)
            ),
            createServer(
                serverId = "server1",
                loadPercent = 10f,
                statusReference = ServerStatusReference(index = 0u, penalty = 0.0, cost = 0)
            )
        )
        // Index 0: enabled, load 50.
        val updatedServers = applyUpdate(serversToUpdate, "AQAAAAMyAAAAAA==")
        assertEquals(50f, updatedServers["server1"]!!.load, 0.1f)
        assertFalse(updatedServers["server2"]!!.isVisible)
        assertFalse(updatedServers["server2"]!!.online)
    }

    @Test
    fun `servers without status reference are untouched`() {
        val serversToUpdate = listOf(
            createServer(
                serverId = "server",
                loadPercent = 20f,
                statusReference = ServerStatusReference(index = 0u, penalty = 0.0, cost = 0)
            ),
            createServer(
                serverId = "serverInvalid",
                loadPercent = 30f,
                statusReference = null,
            ),
        )

        // Index 0: enabled, load 50. Index 1: disabled, load 75
        val updatedServers = applyUpdate(serversToUpdate, "AQAAAAMyAAAAAAJLAACAPw==")
        assertEquals(50f, updatedServers["server"]!!.load, 0.1f)
        assertEquals(30f, updatedServers["serverInvalid"]!!.load, 0.1f)
        assertTrue(updatedServers["serverInvalid"]!!.isVisible)
        assertTrue(updatedServers["serverInvalid"]!!.online)
    }

    private fun applyUpdate(serversToUpdate: List<Server>, statusDataBase64: String): Map<String, Server> {
        val statusData = Base64.getUrlDecoder().decode(statusDataBase64)
        val result = updater(serversToUpdate, statusData)
        assertNotNull(result)
        return result.associateBy { it.serverId }
    }
}