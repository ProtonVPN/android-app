package com.protonvpn.app

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.protonvpn.android.models.profiles.Profile
import com.protonvpn.android.models.vpn.ConnectionParams
import com.protonvpn.android.models.vpn.Server
import com.protonvpn.android.utils.Storage
import com.protonvpn.android.vpn.RecentsManager
import com.protonvpn.android.vpn.VpnState
import com.protonvpn.android.vpn.VpnStateMonitor
import com.protonvpn.app.mocks.MockSharedPreference
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestCoroutineScope
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class RecentsManagerTests {

    private lateinit var manager: RecentsManager

    @RelaxedMockK private lateinit var vpnStateMonitor: VpnStateMonitor

    private val vpnStatus = MutableStateFlow(VpnStateMonitor.Status(VpnState.Disabled, null))

    @get:Rule var rule = InstantTaskExecutorRule()

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        Storage.setPreferences(MockSharedPreference())
        every { vpnStateMonitor.status } returns vpnStatus
        manager = RecentsManager(TestCoroutineScope(), vpnStateMonitor, mockk(relaxed = true), mockk(relaxed = true))
    }

    private fun addRecent(connectionParams: ConnectionParams) {
        vpnStatus.value = VpnStateMonitor.Status(VpnState.Connected, connectionParams)
        vpnStatus.value = VpnStateMonitor.Status(VpnState.Disconnecting, connectionParams)
    }

    private fun mockedConnectionParams(country: String, serverName: String = country): ConnectionParams {
        val profile = Profile(country, "", mockk(relaxed = true))
        every { profile.server?.exitCountry }.returns(country)
        val server = mockk<Server>()
        every { server.flag }.returns(country)
        every { server.serverName }.returns(serverName)
        return ConnectionParams(profile, server, mockk(), mockk())
    }

    @Test
    fun testAddingNewServerOnlyAfterDisconnectingState() {
        val connectionParams = mockedConnectionParams("Test")
        vpnStatus.value = VpnStateMonitor.Status(VpnState.Connected, connectionParams)
        Assert.assertEquals(0, manager.getRecentCountries().size)
        vpnStatus.value = VpnStateMonitor.Status(VpnState.Disconnecting, connectionParams)
        Assert.assertEquals(1, manager.getRecentCountries().size)
    }

    @Test
    fun testNewlyUsedRecentsMovedToFront() {
        val connectionParams = mockedConnectionParams("Test")
        addRecent(mockk(relaxed = true))
        addRecent(mockk(relaxed = true))
        addRecent(connectionParams)
        addRecent(mockedConnectionParams("Test2"))
        Assert.assertNotEquals(connectionParams.profile.name, manager.getRecentCountries()[0].name)
        addRecent(connectionParams)
        Assert.assertEquals(connectionParams.profile.name, manager.getRecentCountries()[0].name)
    }

    @Test
    fun testRecentServers() {
        addRecent(mockedConnectionParams("A", "A1"))
        addRecent(mockedConnectionParams("A", "A2"))
        addRecent(mockedConnectionParams("A", "A3"))
        addRecent(mockedConnectionParams("A", "A4"))
        addRecent(mockedConnectionParams("A", "A3"))
        addRecent(mockedConnectionParams("B", "B1"))

        Assert.assertEquals(listOf("A3", "A4", "A2"), manager.getRecentServers("A")?.map { it.serverName })
        Assert.assertEquals(listOf("B1"), manager.getRecentServers("B")?.map { it.serverName })
    }
}
