package com.protonvpn.app.redesign.main_screen.ui

import app.cash.turbine.test
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.managed.AutoLoginManager
import com.protonvpn.android.models.vpn.usecase.SupportsProtocol
import com.protonvpn.android.redesign.app.ui.MainActivityViewModel
import com.protonvpn.android.redesign.vpn.ui.VpnStatusViewStateFlow
import com.protonvpn.android.servers.Server
import com.protonvpn.android.servers.ServerManager2
import com.protonvpn.android.ui.storage.UiStateStorage
import com.protonvpn.android.ui.storage.UiStateStoreProvider
import com.protonvpn.android.utils.ServerManager
import com.protonvpn.android.utils.Storage
import com.protonvpn.android.vpn.VpnConnectionManager
import com.protonvpn.mocks.FakeShouldShowAppUpdateDotFlow
import com.protonvpn.mocks.createInMemoryServerManager
import com.protonvpn.test.shared.InMemoryDataStoreFactory
import com.protonvpn.test.shared.MockSharedPreference
import com.protonvpn.test.shared.TestCurrentUserProvider
import com.protonvpn.test.shared.TestDispatcherProvider
import com.protonvpn.test.shared.TestUser
import com.protonvpn.test.shared.createGetSmartProtocols
import com.protonvpn.test.shared.createServer
import io.mockk.MockKAnnotations
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class MainActivityViewModelTests {

    @MockK
    private lateinit var mockAutoLoginManager: AutoLoginManager

    @MockK
    private lateinit var mockVpnConnectionManager: VpnConnectionManager

    @MockK
    private lateinit var mockVpnStatusViewStateFlow: VpnStatusViewStateFlow

    private lateinit var serverManager: ServerManager

    private lateinit var serverManager2: ServerManager2

    private lateinit var testScope: TestScope

    private lateinit var uiStateStorage: UiStateStorage

    private lateinit var viewModel: MainActivityViewModel

    @Before
    fun setup() {
        MockKAnnotations.init(this)

        Storage.setPreferences(MockSharedPreference())

        val testDispatcher = UnconfinedTestDispatcher()

        // Required setting main dispatcher due to usage of: ServerManager
        Dispatchers.setMain(testDispatcher)

        val currentUser = CurrentUser(TestCurrentUserProvider(TestUser.plusUser.vpnUser))

        val supportsProtocol = SupportsProtocol(createGetSmartProtocols())

        testScope = TestScope(testDispatcher)

        serverManager = createInMemoryServerManager(
            testScope = testScope,
            testDispatcherProvider = TestDispatcherProvider(testDispatcher),
            supportsProtocol = supportsProtocol,
            initialServers = emptyList(),
        )

        serverManager2 = ServerManager2(
            serverManager = serverManager,
            supportsProtocol = supportsProtocol,
        )

        uiStateStorage = UiStateStorage(
            provider = UiStateStoreProvider(factory = InMemoryDataStoreFactory()),
            currentUser = currentUser,
        )

        viewModel = MainActivityViewModel(
            autoLoginManager = mockAutoLoginManager,
            serverManager2 = serverManager2,
            uiStateStorage = uiStateStorage,
            vpnConnectionManager = mockVpnConnectionManager,
            vpnStatusViewStateFlow = mockVpnStatusViewStateFlow,
            shouldShowAppUpdateDotFlow = FakeShouldShowAppUpdateDotFlow()
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `GIVEN there are no servers WHEN showCountriesFlow is observed THEN emits false`() = testScope.runTest {
        val serverList = emptyList<Server>()
        val expectedShowCountries = false
        serverManager.setServers(serverList = serverList, statusId = "1", language = null)

        viewModel.showCountriesFlow.test {
            val showCountries = awaitItem()

            assertEquals(expectedShowCountries, showCountries)
        }
    }

    @Test
    fun `GIVEN there are servers WHEN showCountriesFlow is observed THEN emits true`() = testScope.runTest {
        val serverList = listOf(createServer(serverId = "server1"))
        val expectedShowCountries = true
        serverManager.setServers(serverList = serverList, statusId = "1", language = null)

        viewModel.showCountriesFlow.test {
            val showCountries = awaitItem()

            assertEquals(expectedShowCountries, showCountries)
        }
    }

    @Test
    fun `GIVEN there are no servers WHEN showGatewaysFlow is observed THEN emits false`() = testScope.runTest {
        val serverList = emptyList<Server>()
        val expectedShowGateways = false
        serverManager.setServers(serverList = serverList, statusId = "1", language = null)

        viewModel.showGatewaysFlow.test {
            val showGateways = awaitItem()

            assertEquals(expectedShowGateways, showGateways)
        }
    }

    @Test
    fun `GIVEN there are no servers with gateways WHEN showGatewaysFlow is observed THEN emits false`() = testScope.runTest {
        val serverList = listOf(createServer(serverId = "server1", gatewayName = null))
        val expectedShowGateways = false
        serverManager.setServers(serverList = serverList, statusId = "1", language = null)

        viewModel.showGatewaysFlow.test {
            val showGateways = awaitItem()

            assertEquals(expectedShowGateways, showGateways)
        }
    }

    @Test
    fun `GIVEN there are servers with gateways WHEN showGatewaysFlow is observed THEN emits true`() = testScope.runTest {
        val serverList = listOf(createServer(serverId = "server1", gatewayName = "gateway1"))
        val expectedShowGateways = true
        serverManager.setServers(serverList = serverList, statusId = "1", language = null)

        viewModel.showGatewaysFlow.test {
            val showGateways = awaitItem()

            assertEquals(expectedShowGateways, showGateways)
        }
    }

}
