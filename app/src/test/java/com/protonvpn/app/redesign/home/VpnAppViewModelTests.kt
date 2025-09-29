/*
 * Copyright (c) 2024. Proton AG
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
package com.protonvpn.app.redesign.home

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import app.cash.turbine.test
import com.protonvpn.android.api.ProtonApiRetroFit
import com.protonvpn.android.appconfig.periodicupdates.UpdateState
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.managed.ManagedConfig
import com.protonvpn.android.models.vpn.usecase.SupportsProtocol
import com.protonvpn.android.redesign.app.ui.VpnAppViewModel
import com.protonvpn.android.servers.ServerManager2
import com.protonvpn.android.servers.UpdateServerListFromApi
import com.protonvpn.android.ui.home.ServerListUpdater
import com.protonvpn.android.utils.ServerManager
import com.protonvpn.android.utils.Storage
import com.protonvpn.android.utils.UserPlanManager
import com.protonvpn.mocks.createInMemoryServerManager
import com.protonvpn.test.shared.ImmediatePeriodicUpdateManager
import com.protonvpn.test.shared.MockSharedPreference
import com.protonvpn.test.shared.TestCurrentUserProvider
import com.protonvpn.test.shared.TestDispatcherProvider
import com.protonvpn.test.shared.TestSetVpnUser
import com.protonvpn.test.shared.TestUser
import com.protonvpn.test.shared.createAccountUser
import com.protonvpn.test.shared.createGetSmartProtocols
import com.protonvpn.test.shared.createServer
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import me.proton.core.network.domain.ApiResult
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class VpnAppViewModelTests {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()
    private lateinit var testScope: TestScope

    @MockK
    private lateinit var mockApi: ProtonApiRetroFit

    @MockK
    private lateinit var mockServerListUpdater: ServerListUpdater

    private lateinit var currentUser: CurrentUser
    private lateinit var serverListUpdaterState: MutableStateFlow<UpdateState<UpdateServerListFromApi.Result>>
    private lateinit var serverManager: ServerManager
    private lateinit var serverManager2: ServerManager2
    private lateinit var testUserProvider: TestCurrentUserProvider
    private lateinit var userPlanManager: UserPlanManager


    private val updateServerListError = UpdateServerListFromApi.Result.Error(ApiResult.Error.Timeout(true, null))
    private val vpnInfoSuccessResponse = ApiResult.Success(TestUser.plusUser.vpnInfoResponse)
    private val vpnInfoNeedsConnectionsAssigned =
        ApiResult.Error.Http(422, "", ApiResult.Error.ProtonData(code = 86_300, ""))
    private val vpnUser = TestUser.plusUser.vpnUser
    private val user = createAccountUser(vpnUser.userId)

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        Storage.setPreferences(MockSharedPreference())
        val testDispatcher = StandardTestDispatcher()
        testScope = TestScope(testDispatcher)
        Dispatchers.setMain(testDispatcher)

        // TODO: we need to refactor ServerListUpdater into smaller classes.
        //  This test should use a slimmed down real implementation with only API calls mocked, not its internal state.
        serverListUpdaterState = MutableStateFlow(UpdateState.Idle(null))
        coEvery { mockServerListUpdater.updateState } returns serverListUpdaterState

        testUserProvider = TestCurrentUserProvider(vpnUser = null, user = user, noVpnUserSessionId = vpnUser.sessionId)
        currentUser = CurrentUser(testUserProvider)

        coEvery { mockApi.getVPNInfo(any()) } returns vpnInfoSuccessResponse

        val supportsProtocol = SupportsProtocol(createGetSmartProtocols())
        serverManager = createInMemoryServerManager(
            testScope = testScope,
            testDispatcherProvider = TestDispatcherProvider(testDispatcher),
            supportsProtocol = supportsProtocol,
            initialServers = emptyList()
        )
        serverManager2 = ServerManager2(serverManager, supportsProtocol)

        userPlanManager = UserPlanManager(
            mainScope = testScope.backgroundScope,
            api = mockApi,
            currentUser = currentUser,
            setVpnUser = TestSetVpnUser(testUserProvider),
            managedConfig = ManagedConfig(MutableStateFlow(null)),
            periodicUpdateManager = ImmediatePeriodicUpdateManager(),
            wallClock = { testScope.currentTime },
            inForeground = flowOf(true),
        )
        // Most tests expect a fully logged in user.
        testUserProvider.vpnUser = vpnUser
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `GIVEN servers are not loaded AND servers update succeeds with countries WHEN observing loadingState THEN Loaded state is emitted`() = runTest {
        val viewModel = createViewModel()
        serverManager.setServers(listOf(createServer()), "1", null)

        viewModel.loadingState.test {
            runCurrent()
            val loaderState = expectMostRecentItem()

            assertEquals(VpnAppViewModel.LoaderState.Loaded, loaderState)
        }
    }

    @Test
    fun `GIVEN servers are not loaded AND servers update succeeds with gateways WHEN observing loadingState THEN Loaded state is emitted`() = runTest {
        val viewModel = createViewModel()
        serverManager.setServers(listOf(createServer(gatewayName = "gateway")), "1", null)

        viewModel.loadingState.test {
            runCurrent()
            val loaderState = expectMostRecentItem()

            assertEquals(VpnAppViewModel.LoaderState.Loaded, loaderState)
        }
    }

    @Test
    fun `GIVEN servers are not loaded AND servers update succeeds with no countries_gateways WHEN observing loadingState THEN NoCountriesNoGateways state is emitted`() = runTest {
        val viewModel = createViewModel()
        serverListUpdaterState.value = UpdateState.Idle(UpdateServerListFromApi.Result.Success)

        viewModel.loadingState.test {
            runCurrent()
            val loaderState = expectMostRecentItem()

            assertIs<VpnAppViewModel.LoaderState.Error.NoCountriesNoGateways>(loaderState)
        }
    }

    @Test
    fun `GIVEN servers are not loaded AND servers update fails WHEN observing loadingState THEN RequestFailed state is emitted`() = runTest {
        val viewModel = createViewModel()
        serverListUpdaterState.value = UpdateState.Idle(updateServerListError)

        viewModel.loadingState.test {
            runCurrent()
            val loaderState = expectMostRecentItem()

            assertIs<VpnAppViewModel.LoaderState.Error.RequestFailed>(loaderState)
        }
    }

    @Test
    fun `GIVEN servers are loaded AND there are countries WHEN observing loadingState THEN Loaded state is emitted`() = runTest {
        serverManager.setServers(listOf(createServer()), "1", null)
        val viewModel = createViewModel()
        val expectedLoaderState = VpnAppViewModel.LoaderState.Loaded

        viewModel.loadingState.test {
            runCurrent()
            assertEquals(expectedLoaderState, expectMostRecentItem())
        }
    }

    @Test
    fun `GIVEN servers are loaded AND there are gateways WHEN observing loadingState THEN Loaded state is emitted`() = runTest {
        serverManager.setServers(listOf(createServer(gatewayName = "gateway")), "1", null)
        val viewModel = createViewModel()

        viewModel.loadingState.test {
            runCurrent()
            assertEquals(VpnAppViewModel.LoaderState.Loaded, expectMostRecentItem())
        }
    }

    @Test
    fun `GIVEN user needs connections assigned WHEN observing loadingState THEN DisabledByAdmin is emitted`() = runTest {
        serverManager.setServers(listOf(createServer()), "1", null)
        testUserProvider.vpnUser = null
        coEvery { mockApi.getVPNInfo(any()) } returns vpnInfoNeedsConnectionsAssigned

        val viewModel = createViewModel()
        userPlanManager.refreshVpnInfo()
        viewModel.loadingState.test {
            runCurrent()
            assertIs<VpnAppViewModel.LoaderState.Error.DisabledByAdmin>( expectMostRecentItem())
        }
    }

    @Test
    fun `GIVEN user with assigned connections WHEN connections are removed THEN DisabledByAdmin is emitted`() = runTest {
        serverManager.setServers(listOf(createServer()), "1", null)

        val viewModel = createViewModel()
        userPlanManager.refreshVpnInfo()
        viewModel.loadingState.test {
            runCurrent()
            assertEquals(VpnAppViewModel.LoaderState.Loaded, expectMostRecentItem())

            coEvery { mockApi.getVPNInfo(any()) } returns vpnInfoNeedsConnectionsAssigned
            userPlanManager.refreshVpnInfo()
            runCurrent()
            assertIs<VpnAppViewModel.LoaderState.Error.DisabledByAdmin>( expectMostRecentItem())
        }
    }

    @Test
    fun `GIVEN user needs connections assigned WHEN connections are set THEN Loaded is emitted`() = runTest {
        serverManager.setServers(listOf(createServer()), "1", null)
        coEvery { mockApi.getVPNInfo(any()) } returns vpnInfoNeedsConnectionsAssigned
        testUserProvider.vpnUser = null

        val viewModel = createViewModel()
        userPlanManager.refreshVpnInfo()
        viewModel.loadingState.test {
            runCurrent()
            assertIs<VpnAppViewModel.LoaderState.Error.DisabledByAdmin>(expectMostRecentItem())

            coEvery { mockApi.getVPNInfo(any()) } returns vpnInfoSuccessResponse
            userPlanManager.refreshVpnInfo()
            runCurrent()
            assertEquals(VpnAppViewModel.LoaderState.Loaded, expectMostRecentItem())
        }
    }

    private fun TestScope.createViewModel() = VpnAppViewModel(
        mainScope = backgroundScope,
        serverManager = serverManager2,
        serverListUpdater = mockServerListUpdater,
        currentUser = currentUser,
        userPlanManager = userPlanManager,
        guestHole = mockk(relaxed = true),
    )
}
