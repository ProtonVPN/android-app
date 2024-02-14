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
import com.protonvpn.android.models.vpn.ServerList
import com.protonvpn.android.redesign.app.ui.ServerLoadingViewModel
import com.protonvpn.android.servers.ServerManager2
import com.protonvpn.android.ui.home.ServerListUpdater
import com.protonvpn.test.shared.runWhileCollecting
import io.mockk.Called
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
class ServerLoadingViewModelTests {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()
    private lateinit var testScope: TestScope

    @RelaxedMockK
    private lateinit var serverManager: ServerManager2

    @RelaxedMockK
    private lateinit var serverListUpdater: ServerListUpdater

    private val apiSuccess = ApiResult.Success(ServerList(emptyList()))
    private val apiError = ApiResult.Error.Timeout(true, null)

    @OptIn(ExperimentalCoroutinesApi::class)
    @Before
    fun setup() {
        MockKAnnotations.init(this)
        val testDispatcher = UnconfinedTestDispatcher(TestCoroutineScheduler())
        testScope = TestScope(testDispatcher)
        Dispatchers.setMain(testDispatcher)
        every { serverManager.isDownloadedAtLeastOnceFlow } returns flowOf(false)
        coEvery { serverListUpdater.updateServerList() } returns ApiResult.Success(mockk())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `state is loaded if servers are downloaded at least once`() = runTest {
        every { serverManager.isDownloadedAtLeastOnceFlow } returns flowOf(true)
        val viewModel = ServerLoadingViewModel(serverManager, serverListUpdater)

        coVerify { serverListUpdater.updateServerList() wasNot Called }

        val state = viewModel.serverLoadingState.first()
        assertIs<ServerLoadingViewModel.LoaderState.Loaded>(state)
    }

    @Test
    fun `state changes to loaded on successful update`() = runTest {
        val viewModel = ServerLoadingViewModel(serverManager, serverListUpdater)
        viewModel.updateServerList()

        val state = viewModel.serverLoadingState.first()
        assertIs<ServerLoadingViewModel.LoaderState.Loaded>(state)
    }

    @Test
    fun `state changes to error on failed update`() = runTest {
        every { serverManager.isDownloadedAtLeastOnceFlow } returns flowOf(false)
        coEvery { serverListUpdater.updateServerList() } returns ApiResult.Error.Timeout(true, null)
        val viewModel = ServerLoadingViewModel(serverManager, serverListUpdater)

        val state = viewModel.serverLoadingState.first()
        assertIs<ServerLoadingViewModel.LoaderState.Error>(state)
    }

    @Test
    fun `error state is cleared when retrying`() = runTest {
        val suspendedResponse = CompletableDeferred<ApiResult<ServerList>>()
        coEvery { serverListUpdater.updateServerList() } returns apiError
        val viewModel = ServerLoadingViewModel(serverManager, serverListUpdater)

        val states = runWhileCollecting(viewModel.serverLoadingState) {
            runCurrent()

            coEvery { serverListUpdater.updateServerList() } coAnswers { suspendedResponse.await() }
            viewModel.updateServerList()
            runCurrent()

            suspendedResponse.complete(apiSuccess)
            runCurrent()
        }
        val expected = listOf(
            ServerLoadingViewModel.LoaderState.Error,
            ServerLoadingViewModel.LoaderState.Loading,
            ServerLoadingViewModel.LoaderState.Loaded,
        )
        assertEquals(expected, states)
    }
}
