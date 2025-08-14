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
import com.protonvpn.android.models.vpn.ServerList
import com.protonvpn.android.redesign.app.ui.ServerLoadingViewModel
import com.protonvpn.android.servers.ServerManager2
import com.protonvpn.android.ui.home.ServerListUpdater
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import me.proton.core.network.domain.ApiResult
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ServerLoadingViewModelTests {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()
    private lateinit var testScope: TestScope

    @MockK
    private lateinit var serverManager: ServerManager2

    @MockK
    private lateinit var serverListUpdater: ServerListUpdater

    private val apiSuccess = ApiResult.Success(ServerList(emptyList()))
    private val apiError = ApiResult.Error.Timeout(true, null)

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        val testDispatcher = UnconfinedTestDispatcher(TestCoroutineScheduler())
        testScope = TestScope(testDispatcher)
        Dispatchers.setMain(testDispatcher)
        every { serverManager.isDownloadedAtLeastOnceFlow } returns flowOf(false)
        coEvery { serverListUpdater.updateServerList(any()) } returns ApiResult.Success(mockk())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `GIVEN servers are not loaded WHEN viewModel is created THEN servers are fetched`() = runTest {
        every { serverManager.isDownloadedAtLeastOnceFlow } returns flowOf(false)
        every { serverManager.hasAnyCountryFlow } returns flowOf(true)
        every { serverManager.hasAnyGatewaysFlow } returns flowOf(true)

        ServerLoadingViewModel(serverManager, serverListUpdater)

        coVerify(exactly = 1) {
            serverListUpdater.updateServerList(forceFreshUpdate = true)
        }
    }

    @Test
    fun `GIVEN servers are not loaded AND servers update succeeds with countries WHEN observing serverLoadingState THEN Loaded state is emitted`() = runTest {
        every { serverManager.isDownloadedAtLeastOnceFlow } returns flowOf(false)
        every { serverManager.hasAnyCountryFlow } returns flowOf(true)
        every { serverManager.hasAnyGatewaysFlow } returns flowOf(false)
        coEvery { serverListUpdater.updateServerList(forceFreshUpdate = true) } returns apiSuccess
        val viewModel = ServerLoadingViewModel(serverManager, serverListUpdater)
        val expectedLoaderState = ServerLoadingViewModel.LoaderState.Loaded

        viewModel.serverLoadingState.test {
            val loaderState = awaitItem()

            assertEquals(expectedLoaderState, loaderState)
        }
    }

    @Test
    fun `GIVEN servers are not loaded AND servers update succeeds with gateways WHEN observing serverLoadingState THEN Loaded state is emitted`() = runTest {
        every { serverManager.isDownloadedAtLeastOnceFlow } returns flowOf(false)
        every { serverManager.hasAnyCountryFlow } returns flowOf(false)
        every { serverManager.hasAnyGatewaysFlow } returns flowOf(true)
        coEvery { serverListUpdater.updateServerList(forceFreshUpdate = true) } returns apiSuccess
        val viewModel = ServerLoadingViewModel(serverManager, serverListUpdater)
        val expectedLoaderState = ServerLoadingViewModel.LoaderState.Loaded

        viewModel.serverLoadingState.test {
            val loaderState = awaitItem()

            assertEquals(expectedLoaderState, loaderState)
        }
    }

    @Test
    fun `GIVEN servers are not loaded AND servers update succeeds with no countries_gateways WHEN observing serverLoadingState THEN NoCountriesNoGateways state is emitted`() = runTest {
        every { serverManager.isDownloadedAtLeastOnceFlow } returns flowOf(false)
        every { serverManager.hasAnyCountryFlow } returns flowOf(false)
        every { serverManager.hasAnyGatewaysFlow } returns flowOf(false)
        coEvery { serverListUpdater.updateServerList(forceFreshUpdate = true) } returns apiSuccess
        val viewModel = ServerLoadingViewModel(serverManager, serverListUpdater)
        val expectedLoaderState = ServerLoadingViewModel.LoaderState.Error.NoCountriesNoGateways

        viewModel.serverLoadingState.test {
            val loaderState = awaitItem()

            assertEquals(expectedLoaderState, loaderState)
        }
    }

    @Test
    fun `GIVEN servers are not loaded AND servers update fails WHEN observing serverLoadingState THEN RequestFailed state is emitted`() = runTest {
        listOf(
            true to true,
            true to false,
            false to true,
            false to false,
        ).forEach { (hasCountries, hasGateways) ->
            every { serverManager.isDownloadedAtLeastOnceFlow } returns flowOf(false)
            every { serverManager.hasAnyCountryFlow } returns flowOf(hasCountries)
            every { serverManager.hasAnyGatewaysFlow } returns flowOf(hasGateways)
            coEvery { serverListUpdater.updateServerList(forceFreshUpdate = true) } returns apiError
            val viewModel = ServerLoadingViewModel(serverManager, serverListUpdater)
            val expectedLoaderState = ServerLoadingViewModel.LoaderState.Error.RequestFailed

            viewModel.serverLoadingState.test {
                val loaderState = awaitItem()

                assertEquals(expectedLoaderState, loaderState)
            }
        }
    }

    @Test
    fun `GIVEN servers are loaded AND there are countries WHEN observing serverLoadingState THEN Loaded state is emitted`() = runTest {
        every { serverManager.isDownloadedAtLeastOnceFlow } returns flowOf(true)
        every { serverManager.hasAnyCountryFlow } returns flowOf(true)
        every { serverManager.hasAnyGatewaysFlow } returns flowOf(false)
        val viewModel = ServerLoadingViewModel(serverManager, serverListUpdater)
        val expectedLoaderState = ServerLoadingViewModel.LoaderState.Loaded

        viewModel.serverLoadingState.test {
            val loaderState = awaitItem()

            assertEquals(expectedLoaderState, loaderState)
        }
    }

    @Test
    fun `GIVEN servers are loaded AND there are gateways WHEN observing serverLoadingState THEN Loaded state is emitted`() = runTest {
        every { serverManager.isDownloadedAtLeastOnceFlow } returns flowOf(true)
        every { serverManager.hasAnyCountryFlow } returns flowOf(false)
        every { serverManager.hasAnyGatewaysFlow } returns flowOf(true)
        val viewModel = ServerLoadingViewModel(serverManager, serverListUpdater)
        val expectedLoaderState = ServerLoadingViewModel.LoaderState.Loaded

        viewModel.serverLoadingState.test {
            val loaderState = awaitItem()

            assertEquals(expectedLoaderState, loaderState)
        }
    }

    @Test
    fun `GIVEN servers are loaded AND there are no countries and no gateways WHEN observing serverLoadingState THEN NoCountriesNoGateways state is emitted`() = runTest {
        every { serverManager.isDownloadedAtLeastOnceFlow } returns flowOf(true)
        every { serverManager.hasAnyCountryFlow } returns flowOf(false)
        every { serverManager.hasAnyGatewaysFlow } returns flowOf(false)
        val viewModel = ServerLoadingViewModel(serverManager, serverListUpdater)
        val expectedLoaderState = ServerLoadingViewModel.LoaderState.Error.NoCountriesNoGateways

        viewModel.serverLoadingState.test {
            val loaderState = awaitItem()

            assertEquals(expectedLoaderState, loaderState)
        }
    }
}
