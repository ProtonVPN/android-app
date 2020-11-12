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

    @Test
    fun testAddingNewServerOnlyAfterDisconnectingState() {
        vpnStatus.value = VpnStateMonitor.Status(VpnState.Connected, mockk(relaxed = true))
        Assert.assertEquals(0, manager.getRecentConnections().size)
        vpnStatus.value = VpnStateMonitor.Status(VpnState.Disconnecting, mockk(relaxed = true))
        Assert.assertEquals(1, manager.getRecentConnections().size)
    }

    @Test
    fun testNewlyUsedRecentsMovedToFront() {
        val profile = Profile("Test", "", mockk(relaxed = true))
        val connectionParams = ConnectionParams(profile, mockk(), mockk(), mockk())
        addRecent(mockk(relaxed = true))
        addRecent(mockk(relaxed = true))
        addRecent(connectionParams)
        addRecent(mockk(relaxed = true))
        Assert.assertNotEquals(profile.name, manager.getRecentConnections()[0].name)
        addRecent(connectionParams)
        Assert.assertEquals(profile.name, manager.getRecentConnections()[0].name)
    }
}