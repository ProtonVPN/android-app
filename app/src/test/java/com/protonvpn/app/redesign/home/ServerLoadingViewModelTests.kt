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
import com.protonvpn.android.redesign.main_screen.ui.ServerLoadingViewModel
import com.protonvpn.android.servers.ServerManager2
import com.protonvpn.android.ui.home.ServerListUpdater
import io.mockk.Called
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import me.proton.core.network.domain.ApiResult
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class ServerLoadingViewModelTest {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()
    private lateinit var testScope: TestScope

    @RelaxedMockK
    private lateinit var serverManager: ServerManager2

    @RelaxedMockK
    private lateinit var serverListUpdater: ServerListUpdater

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

    @Test
    fun `state is loaded if servers are downloaded at least once`() = runTest {
        every { serverManager.isDownloadedAtLeastOnceFlow } returns flowOf(true)
        val viewModel = ServerLoadingViewModel(serverManager, serverListUpdater)

        coVerify { serverListUpdater.updateServerList() wasNot Called }

        val state = viewModel.serverLoadingState.first()
        Assert.assertTrue(
            "Expected state to be Loaded, but was ${state::class.simpleName}",
            state is ServerLoadingViewModel.LoaderState.Loaded
        )
    }

    @Test
    fun `state changes to loaded on successful update`() = runTest {
        val viewModel = ServerLoadingViewModel(serverManager, serverListUpdater)
        viewModel.updateServerList()

        val state = viewModel.serverLoadingState.first()
        Assert.assertTrue(
            "Expected state to be Loaded, but was ${state::class.simpleName}",
            state is ServerLoadingViewModel.LoaderState.Loaded
        )
    }

    @Test
    fun `state changes to error on failed update`() = runTest {
        every { serverManager.isDownloadedAtLeastOnceFlow } returns flowOf(false)
        coEvery { serverListUpdater.updateServerList() } returns ApiResult.Error.Timeout(true, null)
        val viewModel = ServerLoadingViewModel(serverManager, serverListUpdater)

        val state = viewModel.serverLoadingState.first()
        Assert.assertTrue(
            "Expected state to be Error, but was ${state::class.simpleName}",
            state is ServerLoadingViewModel.LoaderState.Error
        )
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }
}
