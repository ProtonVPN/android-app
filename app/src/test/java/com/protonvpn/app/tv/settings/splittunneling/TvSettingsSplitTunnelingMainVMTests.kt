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

package com.protonvpn.app.tv.settings.splittunneling

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import app.cash.turbine.turbineScope
import com.protonvpn.android.components.InstalledAppsProvider
import com.protonvpn.android.models.vpn.ConnectionParams
import com.protonvpn.android.redesign.settings.ui.SettingsReconnectHandler
import com.protonvpn.android.redesign.vpn.ConnectIntent
import com.protonvpn.android.settings.data.CurrentUserLocalSettingsManager
import com.protonvpn.android.settings.data.LocalUserSettingsStoreProvider
import com.protonvpn.android.settings.data.SplitTunnelingMode
import com.protonvpn.android.tv.settings.splittunneling.TvSettingsSplitTunnelingMainVM
import com.protonvpn.android.userstorage.DontShowAgainStore
import com.protonvpn.android.vpn.VpnConnectionManager
import com.protonvpn.android.vpn.VpnState
import com.protonvpn.android.vpn.VpnStateMonitor
import com.protonvpn.android.vpn.VpnStatusProviderUI
import com.protonvpn.android.vpn.VpnUiDelegate
import com.protonvpn.test.shared.InMemoryDataStoreFactory
import com.protonvpn.test.shared.awaitMatchingItem
import com.protonvpn.test.shared.createServer
import io.mockk.MockKAnnotations
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.just
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TvSettingsSplitTunnelingMainVMTests {

    @get:Rule
    var instantExecutorRule = InstantTaskExecutorRule()

    @MockK
    private lateinit var mockAppsProvider: InstalledAppsProvider
    @MockK
    private lateinit var mockDontShowAgainStore: DontShowAgainStore
    @MockK
    private lateinit var mockVpnConnectionManager: VpnConnectionManager
    @MockK
    private lateinit var mockVpnUiDelegate: VpnUiDelegate

    private lateinit var testScope: TestScope
    private lateinit var userSettingsManager: CurrentUserLocalSettingsManager
    private lateinit var vpnStateMonitor: VpnStateMonitor

    private lateinit var viewModel: TvSettingsSplitTunnelingMainVM

    private val connectionParams = ConnectionParams(ConnectIntent.Fastest, createServer(), null, null)

    @Before
    fun setup() {
        MockKAnnotations.init(this)

        val dispatcher = UnconfinedTestDispatcher()
        Dispatchers.setMain(dispatcher)
        testScope = TestScope(dispatcher)

        userSettingsManager = CurrentUserLocalSettingsManager(
            LocalUserSettingsStoreProvider(InMemoryDataStoreFactory(), null)
        )
        vpnStateMonitor = VpnStateMonitor()
        val vpnStatusProviderUi = VpnStatusProviderUI(testScope.backgroundScope, vpnStateMonitor)
        val savedStateHandle = SavedStateHandle()

        coEvery { mockDontShowAgainStore.getChoice(any()) } returns DontShowAgainStore.Choice.ShowDialog
        every { mockVpnConnectionManager.reconnect(any(), any()) } just Runs
        coEvery { mockAppsProvider.getNamesOfInstalledApps(any()) } answers { firstArg() }

        viewModel = TvSettingsSplitTunnelingMainVM(
            testScope.backgroundScope,
            mockAppsProvider,
            userSettingsManager,
            SettingsReconnectHandler(
                testScope.backgroundScope,
                mockVpnConnectionManager,
                vpnStatusProviderUi,
                mockDontShowAgainStore,
                savedStateHandle,
            ),
            mockVpnConnectionManager,
            vpnStatusProviderUi,
            savedStateHandle,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `navigate back without changes proceeds immediately`() = testScope.runTest {
        testBackAndDialog(expectedDialog = false, expectedNavigateBack = true) {
            viewModel.onNavigateBack(mockVpnUiDelegate)
        }
    }

    @Test
    fun `navigate back with changes while not connected proceeds immediately`() = testScope.runTest {
        userSettingsManager.updateSplitTunnelSettings {
            it.copy(isEnabled = true, mode = SplitTunnelingMode.INCLUDE_ONLY, includedApps = listOf("app"))
        }
        testBackAndDialog(expectedDialog = false, expectedNavigateBack = true) {
            viewModel.onNavigateBack(mockVpnUiDelegate)
        }
    }

    @Test
    fun `navigate back with changes while connected triggers reconnect dialog`() = testScope.runTest {
        userSettingsManager.updateSplitTunnelSettings {
            it.copy(isEnabled = true, mode = SplitTunnelingMode.INCLUDE_ONLY, includedApps = listOf("app"))
        }
        vpnStateMonitor.updateStatus(VpnStateMonitor.Status(VpnState.Connected, connectionParams))
        testBackAndDialog(expectedDialog = true, expectedNavigateBack = false) {
            viewModel.onNavigateBack(mockVpnUiDelegate)
        }
    }

    @Test
    fun `navigate back after closing reconnect dialog triggered by navigate back`() = testScope.runTest {
        userSettingsManager.updateSplitTunnelSettings {
            it.copy(isEnabled = true, mode = SplitTunnelingMode.INCLUDE_ONLY, includedApps = listOf("app"))
        }
        vpnStateMonitor.updateStatus(VpnStateMonitor.Status(VpnState.Connected, connectionParams))
        turbineScope {
            val backEvents = viewModel.eventNavigateBack.testIn(backgroundScope)

            viewModel.onNavigateBack(mockVpnUiDelegate)
            advanceUntilIdle()
            backEvents.expectNoEvents()

            viewModel.onDialogReconnectClicked(mockVpnUiDelegate, false)
            assertEquals(Unit, backEvents.awaitItem())
        }
    }

    @Test
    fun `navigate back with no changes while connected proceeds immediately`() = testScope.runTest {
        vpnStateMonitor.updateStatus(VpnStateMonitor.Status(VpnState.Connected, connectionParams))
        testBackAndDialog(expectedDialog = false, expectedNavigateBack = true) {
            viewModel.onNavigateBack(mockVpnUiDelegate)
        }
    }

    @Test
    fun `toggling split tunneling while disconnected `() = testScope.runTest {
        userSettingsManager.updateSplitTunnelSettings {
            it.copy(mode = SplitTunnelingMode.INCLUDE_ONLY, includedApps = listOf("app"))
        }
        testBackAndDialog(expectedDialog = false, expectedNavigateBack = false) {
            viewModel.onToggleEnabled()
        }
    }

    @Test
    fun `toggling split tunneling while connected triggers reconnect banner`() = testScope.runTest {
        userSettingsManager.updateSplitTunnelSettings {
            it.copy(mode = SplitTunnelingMode.INCLUDE_ONLY, includedApps = listOf("app"))
        }
        vpnStateMonitor.updateStatus(VpnStateMonitor.Status(VpnState.Connected, connectionParams))
        viewModel.mainViewState
            .filterNotNull()
            .test {
                viewModel.onToggleEnabled()

                awaitMatchingItem { it.needsReconnect }
                assertNull(viewModel.showReconnectDialogFlow.value)
            }
    }

    @Test
    fun `reconnecting hides the reconnect banner`() = testScope.runTest {
        userSettingsManager.updateSplitTunnelSettings {
            it.copy(mode = SplitTunnelingMode.INCLUDE_ONLY, isEnabled = true, includedApps = listOf("app"))
        }
        vpnStateMonitor.updateStatus(VpnStateMonitor.Status(VpnState.Connected, connectionParams))
        viewModel.mainViewState
            .filterNotNull()
            .test {
                // Show the connect banner
                viewModel.onModeChanged(SplitTunnelingMode.EXCLUDE_ONLY)
                awaitMatchingItem { it.needsReconnect }

                viewModel.onBannerReconnect(mockVpnUiDelegate)
                awaitMatchingItem { !it.needsReconnect }
                cancelAndIgnoreRemainingEvents() // App names are refreshed and produce additional update.
            }
    }

    private suspend fun TestScope.testBackAndDialog(
        expectedDialog: Boolean,
        expectedNavigateBack: Boolean,
        block: suspend TestScope.() -> Unit
    ) {
        turbineScope {
            val backEvents = viewModel.eventNavigateBack.testIn(backgroundScope)

            block()
            advanceUntilIdle()

            val expectedDialogType =
                if (expectedDialog) DontShowAgainStore.Type.SplitTunnelingChangeWhenConnected else null
            assertEquals(expectedDialogType, viewModel.showReconnectDialogFlow.value)
            if (expectedNavigateBack) {
                assertEquals(Unit, backEvents.awaitItem())
            } else {
                backEvents.expectNoEvents()
            }
        }
    }
}
