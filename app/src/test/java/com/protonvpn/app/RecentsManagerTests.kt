package com.protonvpn.app

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.MutableLiveData
import com.protonvpn.android.models.profiles.Profile
import com.protonvpn.android.models.vpn.ConnectionParams
import com.protonvpn.android.utils.Storage
import com.protonvpn.android.vpn.RecentsManager
import com.protonvpn.android.vpn.VpnState
import com.protonvpn.android.vpn.VpnStateMonitor
import com.protonvpn.app.mocks.MockSharedPreference
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.mockk
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class RecentsManagerTests {

    private lateinit var manager: RecentsManager

    @RelaxedMockK private lateinit var vpnStateMonitor: VpnStateMonitor

    private var vpnStatus: MutableLiveData<VpnStateMonitor.Status> = MutableLiveData()

    @get:Rule var rule = InstantTaskExecutorRule()

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        Storage.setPreferences(MockSharedPreference())
        every { vpnStateMonitor.vpnStatus }.returns(vpnStatus)
        manager = RecentsManager(vpnStateMonitor, mockk(relaxed = true))
    }

    private fun addRecent(connectionParams: ConnectionParams) {
        vpnStatus.value = VpnStateMonitor.Status(VpnState.Connected, connectionParams)
        vpnStatus.value = VpnStateMonitor.Status(VpnState.Disconnecting, connectionParams)
    }

    private fun mockedConnectionParams(name: String): ConnectionParams {
        val profile = Profile(name, "", mockk(relaxed = true))
        every { profile.server?.exitCountry }.returns(name)
        return ConnectionParams(profile, mockk(), mockk(), mockk())
    }

    @Test
    fun testAddingNewServerOnlyAfterDisconnectingState() {
        val connectionParams = mockedConnectionParams("Test")
        vpnStatus.value = VpnStateMonitor.Status(VpnState.Connected, connectionParams)
        Assert.assertEquals(0, manager.getRecentConnections().size)
        vpnStatus.value = VpnStateMonitor.Status(VpnState.Disconnecting, connectionParams)
        Assert.assertEquals(1, manager.getRecentConnections().size)
    }

    @Test
    fun testNewlyUsedRecentsMovedToFront() {
        val connectionParams = mockedConnectionParams("Test")
        addRecent(mockk(relaxed = true))
        addRecent(mockk(relaxed = true))
        addRecent(connectionParams)
        addRecent(mockedConnectionParams("Test2"))
        Assert.assertNotEquals(connectionParams.profile.name, manager.getRecentConnections()[0].name)
        addRecent(connectionParams)
        Assert.assertEquals(connectionParams.profile.name, manager.getRecentConnections()[0].name)
    }
}