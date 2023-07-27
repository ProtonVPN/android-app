package com.protonvpn.app.ui.home.vpn

import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.models.config.UserData
import com.protonvpn.android.netshield.NetShieldProtocol
import com.protonvpn.android.netshield.NetShieldStats
import com.protonvpn.android.netshield.NetShieldViewState
import com.protonvpn.android.ui.home.vpn.VpnStateViewModel
import com.protonvpn.android.utils.TrafficMonitor
import com.protonvpn.android.vpn.VpnConnectionManager
import com.protonvpn.test.shared.TestCurrentUserProvider
import com.protonvpn.test.shared.TestDispatcherProvider
import com.protonvpn.test.shared.TestUser
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

    @RelaxedMockK
    private lateinit var mockVpnConnectionManager: VpnConnectionManager

    @RelaxedMockK
    private lateinit var mockTrafficMonitor: TrafficMonitor

    @RelaxedMockK
    private lateinit var mockUserData: UserData


    private lateinit var currentUserProvider: TestCurrentUserProvider
    private lateinit var scope: TestScope
    private lateinit var testDispatcherProvider: TestDispatcherProvider
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
        currentUserProvider = TestCurrentUserProvider(TestUser.plusUser.vpnUser)
        val currentUser = CurrentUser(scope.backgroundScope, currentUserProvider)

        every { mockUserData.netShieldStateFlow } returns netshieldProtocolFlow
        every { mockVpnConnectionManager.netShieldStats } returns netshieldStatsFlow
        viewModel = VpnStateViewModel(mockVpnConnectionManager, mockTrafficMonitor, mockUserData, currentUser)
    }

    @Test
    fun `downgrade triggers upgrade state`() = scope.runTest {
        assertIs<NetShieldViewState.NetShieldState>(viewModel.netShieldViewState.value)
        currentUserProvider.vpnUser = TestUser.freeUser.vpnUser
        assertIs<NetShieldViewState.UpgradePlusBanner>(viewModel.netShieldViewState.value)
    }

    @Test
    fun `update to netshield stats are reflected in netshield state`() = scope.runTest {
        assert(getNetShieldState().netShieldStats.adsBlocked == 0L)
        netshieldStatsFlow.emit(NetShieldStats(2))
        assert(getNetShieldState().netShieldStats.adsBlocked == 2L)
    }

    @Test
    fun `vpn-essential plan shows business upgrade banner`() = scope.runTest {
        currentUserProvider.vpnUser = TestUser.businessEssential.vpnUser
        assertIs<NetShieldViewState.UpgradeBusinessBanner>(viewModel.netShieldViewState.value)
    }

    private fun getNetShieldState() = (viewModel.netShieldViewState.value as NetShieldViewState.NetShieldState)
}
