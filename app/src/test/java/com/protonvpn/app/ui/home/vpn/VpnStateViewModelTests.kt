package com.protonvpn.app.ui.home.vpn

import com.protonvpn.android.auth.data.VpnUser
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.models.config.UserData
import com.protonvpn.android.models.profiles.ProfileColor
import com.protonvpn.android.netshield.NetShieldProtocol
import com.protonvpn.android.netshield.NetShieldStats
import com.protonvpn.android.netshield.NetShieldViewState
import com.protonvpn.android.ui.home.vpn.VpnStateViewModel
import com.protonvpn.android.utils.ServerManager
import com.protonvpn.android.utils.TrafficMonitor
import com.protonvpn.android.vpn.VpnConnectionManager
import com.protonvpn.android.vpn.VpnStateMonitor
import com.protonvpn.test.shared.TestDispatcherProvider
import com.protonvpn.test.shared.mockVpnUser
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.RelaxedMockK
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Before
import org.junit.Test
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class VpnStateViewModelTests {

    private val colorId = ProfileColor.CARROT.id

    @RelaxedMockK
    private lateinit var mockVpnStateMonitor: VpnStateMonitor

    @RelaxedMockK
    private lateinit var mockVpnConnectionManager: VpnConnectionManager

    @RelaxedMockK
    private lateinit var mockServerManager: ServerManager

    @RelaxedMockK
    private lateinit var mockTrafficMonitor: TrafficMonitor

    @RelaxedMockK
    private lateinit var mockUserData: UserData

    @RelaxedMockK
    private lateinit var mockCurrentUser: CurrentUser

    @MockK
    lateinit var vpnUser: VpnUser
    private lateinit var scope: TestScope
    private lateinit var testDispatcherProvider: TestDispatcherProvider
    private var userFlow: MutableSharedFlow<VpnUser> = MutableSharedFlow()
    private var netshieldProtocolFlow: MutableStateFlow<NetShieldProtocol> =
        MutableStateFlow(NetShieldProtocol.DISABLED)
    private var netshieldStatsFlow: MutableStateFlow<NetShieldStats> = MutableStateFlow(NetShieldStats())

    private lateinit var viewModel: VpnStateViewModel

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        val testScheduler = TestCoroutineScheduler()
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        scope = TestScope(testDispatcher)
        testDispatcherProvider = TestDispatcherProvider(testDispatcher)
        Dispatchers.setMain(testDispatcher)
        mockCurrentUser.mockVpnUser {
            vpnUser
        }

        every { mockCurrentUser.vpnUserFlow } returns userFlow
        every { mockUserData.netShieldStateFlow } returns netshieldProtocolFlow
        every { mockVpnConnectionManager.netShieldStats } returns netshieldStatsFlow
        viewModel = VpnStateViewModel(
            mockVpnConnectionManager, mockTrafficMonitor, mockUserData, mockCurrentUser
        )
    }

    @Test
    fun `downgrade triggers upgrade state`() = scope.runTest {
        every { vpnUser.isFreeUser } returns false
        userFlow.emit(vpnUser)
        assertIs<NetShieldViewState.NetShieldState>(viewModel.netShieldViewState.value)
        every { vpnUser.isFreeUser } returns true
        userFlow.emit(vpnUser)
        assertIs<NetShieldViewState.UpgradeBanner>(viewModel.netShieldViewState.value)
    }

    @Test
    fun `update to netshield stats are reflected in netshield state`() = scope.runTest {
        every { vpnUser.isFreeUser } returns false
        userFlow.emit(vpnUser)
        assert(getNetShieldState().netShieldStats.adsBlocked == 0L)
        netshieldStatsFlow.emit(NetShieldStats(2))
        assert(getNetShieldState().netShieldStats.adsBlocked == 2L)
    }

    private fun getNetShieldState() = (viewModel.netShieldViewState.value as NetShieldViewState.NetShieldState)
}
