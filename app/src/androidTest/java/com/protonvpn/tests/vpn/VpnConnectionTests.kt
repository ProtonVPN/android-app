/*
 * Copyright (c) 2020 Proton Technologies AG
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
package com.protonvpn.tests.vpn

import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.protonvpn.android.api.ProtonApiManager
import com.protonvpn.android.api.ProtonApiRetroFit
import com.protonvpn.android.models.config.UserData
import com.protonvpn.android.models.config.VpnProtocol
import com.protonvpn.android.models.profiles.Profile
import com.protonvpn.android.ui.home.ServerListUpdater
import com.protonvpn.android.utils.TrafficMonitor
import com.protonvpn.android.vpn.ProtonVpnBackendProvider
import com.protonvpn.android.vpn.VpnStateMonitor
import com.protonvpn.di.MockVpnStateMonitor
import com.protonvpn.mocks.MockVpnBackend
import com.protonvpn.testsHelper.MockedServers
import io.mockk.MockKAnnotations
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.RelaxedMockK
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineScope
import kotlinx.coroutines.test.runBlockingTest
import kotlinx.coroutines.yield
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.coroutines.EmptyCoroutineContext

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
class VpnConnectionTests {

    @get:Rule
    var rule = InstantTaskExecutorRule()

    private lateinit var context: Context
    private lateinit var scope: CoroutineScope
    private lateinit var userData: UserData
    private lateinit var monitor: VpnStateMonitor

    @MockK
    private lateinit var api: ProtonApiRetroFit

    @RelaxedMockK
    lateinit var serverListUpdater: ServerListUpdater

    @RelaxedMockK
    lateinit var trafficMonitor: TrafficMonitor

    @RelaxedMockK
    lateinit var apiManager: ProtonApiManager

    private val mockStrongSwan = MockVpnBackend(VpnProtocol.IKEv2)
    private val mockOpenVpn = MockVpnBackend(VpnProtocol.OpenVPN)

    private lateinit var profileSmart: Profile
    private lateinit var profileIKEv2: Profile

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        context = InstrumentationRegistry.getInstrumentation().context
        scope = TestCoroutineScope(EmptyCoroutineContext)
        userData = UserData()

        val backendProvider =
                ProtonVpnBackendProvider(strongSwan = mockStrongSwan, openVpn = mockOpenVpn)
        monitor = MockVpnStateMonitor(
                userData, api, backendProvider, serverListUpdater, trafficMonitor, apiManager, scope)

        val server = MockedServers.server
        profileSmart = MockedServers.getProfile(VpnProtocol.Smart, server)
        profileIKEv2 = MockedServers.getProfile(VpnProtocol.IKEv2, server)
    }

    @Test
    fun testNotSmartProfile() = runBlockingTest {
        mockStrongSwan.failOnPrepare = true
        monitor.connect(context, profileIKEv2)
        yield()

        val vpnState = monitor.vpnState.value!!
        Assert.assertTrue(vpnState.state is VpnStateMonitor.State.Error)
    }

    @Test
    fun testSmartFallbackToOpenVPN() = runBlockingTest {
        mockStrongSwan.failOnPrepare = true
        monitor.connect(context, profileSmart)
        yield()

        val vpnState = monitor.vpnState.value!!
        Assert.assertEquals(VpnStateMonitor.State.Connected, vpnState.state)
        Assert.assertEquals(VpnProtocol.OpenVPN, vpnState.connectionParams?.protocol)
    }

    @Test
    fun testAllBlocked() = runBlockingTest {
        mockStrongSwan.failOnPrepare = true
        mockOpenVpn.failOnPrepare = true
        monitor.connect(context, profileSmart)
        yield()

        val vpnState = monitor.vpnState.value!!
        Assert.assertTrue((vpnState.state as? VpnStateMonitor.State.Error)?.type ==
                VpnStateMonitor.ErrorType.NO_PORTS_AVAILABLE)
    }
}
