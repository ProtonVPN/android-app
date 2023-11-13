/*
 * Copyright (c) 2022 Proton AG
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
package com.protonvpn.app.vpn

import androidx.activity.ComponentActivity
import com.protonvpn.android.api.GuestHole
import com.protonvpn.android.appconfig.AppFeaturesPrefs
import com.protonvpn.android.models.profiles.Profile
import com.protonvpn.android.models.vpn.ConnectionParams
import com.protonvpn.android.models.vpn.Server
import com.protonvpn.android.ui.ForegroundActivityTracker
import com.protonvpn.android.utils.ServerManager
import com.protonvpn.android.vpn.VpnConnectionManager
import com.protonvpn.android.vpn.VpnState
import com.protonvpn.android.vpn.VpnStateMonitor
import com.protonvpn.test.shared.MockSharedPreferencesProvider
import com.protonvpn.test.shared.MockedServers
import com.protonvpn.test.shared.TestDispatcherProvider
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class GuestHoleTests {

    private lateinit var scope: TestScope
    private lateinit var goodServer: Server
    private lateinit var badServer: Server

    private lateinit var guestHole: GuestHole
    private lateinit var appFeaturesPrefs: AppFeaturesPrefs
    private lateinit var vpnStateMonitor: VpnStateMonitor

    @MockK lateinit var serverManager: ServerManager
    @MockK lateinit var vpnConnectionManager: VpnConnectionManager
    @MockK lateinit var foregroundActivityTracker: ForegroundActivityTracker

    @Before
    fun setup() {
        MockKAnnotations.init(this)

        val dispatcher = StandardTestDispatcher()
        scope = TestScope(dispatcher)
        every { foregroundActivityTracker.foregroundActivity } returns mockk<ComponentActivity>()
        appFeaturesPrefs = AppFeaturesPrefs(MockSharedPreferencesProvider())
        vpnStateMonitor = VpnStateMonitor()

        val servers = MockedServers.serverList
        goodServer = servers[0]
        badServer = servers[1]
        val ghServers = listOf(badServer, goodServer)
        coEvery { serverManager.ensureLoaded() } just runs
        every { serverManager.getGuestHoleServers() } returns ghServers
        coEvery { serverManager.setGuestHoleServers(any()) } just runs
        every { serverManager.getServerById(any()) } answers {
            val id = firstArg<String>()
            ghServers.find { it.serverId == id }
        }
        coEvery { serverManager.isDownloadedAtLeastOnce() } returns false
        every { vpnConnectionManager.connect(any(), any(), any()) } answers {
            val profile = secondArg<Profile>()
            val goodId = profile.directServerId == goodServer.serverId
            val server = if (goodId) goodServer else badServer
            val state = if (goodId) VpnState.Connected else VpnState.Connecting
            val params = ConnectionParams(profile, server, null, null)
            vpnStateMonitor.updateStatus(VpnStateMonitor.Status(state, params))
        }
        coEvery { vpnConnectionManager.disconnectSync(any()) } answers {
            vpnStateMonitor.updateStatus(VpnStateMonitor.Status(VpnState.Disabled, null))
        }

        guestHole = GuestHole(scope, TestDispatcherProvider(dispatcher), { serverManager },
            vpnStateMonitor, { null }, { vpnConnectionManager }, mockk(relaxed = true),
            foregroundActivityTracker, appFeaturesPrefs)
        guestHole.shuffler = { it }
    }

    @Test
    fun successfulServerSaved() = scope.runTest {
        var unblocked = false
        guestHole.onAlternativesUnblock { unblocked = true }
        assertTrue(unblocked)
        assertFalse(vpnStateMonitor.isConnected)
        coVerify(exactly = 2) {
            vpnConnectionManager.connect(any(), any(), any())
        }
        assertEquals(appFeaturesPrefs.lastSuccessfulGuestHoleServerId, goodServer.serverId)

        unblocked = false
        guestHole.onAlternativesUnblock { unblocked = true }
        assertTrue(unblocked)
        coVerify(exactly = 3) { // Saved server is used first on subsequent run
            vpnConnectionManager.connect(any(), any(), any())
        }
    }

    @Test
    fun needGuestHoleLockKeepItOpen() = scope.runTest {
        guestHole.acquireNeedGuestHole("login")
        var unblocked = false
        guestHole.onAlternativesUnblock { unblocked = true }
        assertTrue(unblocked)
        assertTrue(vpnStateMonitor.isConnected)
        guestHole.releaseNeedGuestHole("login")
        assertFalse(vpnStateMonitor.isConnected)
    }

    @Test
    fun runWithGuestHoleFallbackUsesGuestHole() = scope.runTest {
        guestHole.runWithGuestHoleFallback {
            guestHole.onProxiesFailed()
            assertTrue(vpnStateMonitor.isConnected)
        }
        assertFalse(vpnStateMonitor.isConnected)
    }

    @Test
    fun timeoutClosesTheGuestHole() = scope.runTest {
        guestHole.acquireNeedGuestHole("login")

        guestHole.onProxiesFailed()
        assertTrue(vpnStateMonitor.isConnected)
        delay(GuestHole.TIMEOUT_CLOSE_MS)
        // Timeout closes the GH
        assertFalse(vpnStateMonitor.isConnected)
        guestHole.onProxiesFailed()
        // ..but it's reconnected after another proxy fail
        assertTrue(vpnStateMonitor.isConnected)

        guestHole.releaseNeedGuestHole("login")

        assertFalse(vpnStateMonitor.isConnected)
    }

    @Test
    fun proxyFailedIsIgnoredWithoutLock() = scope.runTest {
        guestHole.onProxiesFailed()
        assertFalse(vpnStateMonitor.isConnected)
        coVerify(exactly = 0) { vpnConnectionManager.connect(any(), any(), any()) }
    }

    @Test
    fun nestedNeedGuestHole() = scope.runTest {
        guestHole.runWithGuestHoleFallback {
            guestHole.onProxiesFailed()
            guestHole.runWithGuestHoleFallback {
                guestHole.onProxiesFailed()
                assertTrue(vpnStateMonitor.isConnected)
            }
            // Outer lock should keep GH connected
            assertTrue(vpnStateMonitor.isConnected)
        }
        // Should close only when last lock is removed
        assertFalse(vpnStateMonitor.isConnected)
    }
}
