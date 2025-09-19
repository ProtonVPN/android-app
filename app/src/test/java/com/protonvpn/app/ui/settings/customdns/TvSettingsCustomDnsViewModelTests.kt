/*
 * Copyright (c) 2025 Proton AG
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

package com.protonvpn.app.ui.settings.customdns

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.protonvpn.android.redesign.settings.ui.SettingsReconnectHandler
import com.protonvpn.android.settings.data.CurrentUserLocalSettingsManager
import com.protonvpn.android.settings.data.LocalUserSettingsStoreProvider
import com.protonvpn.android.tv.settings.customdns.TvSettingsCustomDnsViewModel
import com.protonvpn.android.userstorage.DontShowAgainStore
import com.protonvpn.android.vpn.IsPrivateDnsActiveFlow
import com.protonvpn.android.vpn.VpnConnectionManager
import com.protonvpn.android.vpn.VpnStateMonitor
import com.protonvpn.android.vpn.VpnStatusProviderUI
import com.protonvpn.android.vpn.VpnUiDelegate
import com.protonvpn.mocks.FakeVpnUiDelegate
import com.protonvpn.test.shared.InMemoryDataStoreFactory
import io.mockk.MockKAnnotations
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.just
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class TvSettingsCustomDnsViewModelTests {

    @MockK
    private lateinit var mockDontShowAgainStore: DontShowAgainStore

    @MockK
    private lateinit var mockVpnConnectionManager: VpnConnectionManager

    private lateinit var isPrivateDnsActiveFlow: MutableStateFlow<Boolean>

    private lateinit var testScope: TestScope

    private lateinit var userSettingsManager: CurrentUserLocalSettingsManager

    private lateinit var vpnStateMonitor: VpnStateMonitor

    private lateinit var viewModel: TvSettingsCustomDnsViewModel

    private lateinit var vpnUiDelegate: VpnUiDelegate

    @Before
    fun setup() {
        MockKAnnotations.init(this)

        val testDispatcher = UnconfinedTestDispatcher(scheduler = TestCoroutineScheduler())

        testScope = TestScope(context = testDispatcher)

        Dispatchers.setMain(dispatcher = testDispatcher)

        isPrivateDnsActiveFlow = MutableStateFlow(value = false)

        every { mockVpnConnectionManager.reconnect(any(), any()) } just Runs

        vpnUiDelegate = FakeVpnUiDelegate()

        vpnStateMonitor = VpnStateMonitor()

        val vpnStatusProviderUi = VpnStatusProviderUI(
            scope = testScope.backgroundScope,
            monitor = vpnStateMonitor,
        )

        val savedStateHandle = SavedStateHandle()

        val settingsReconnectHandler = SettingsReconnectHandler(
            mainScope = testScope.backgroundScope,
            vpnConnectionManager = mockVpnConnectionManager,
            vpnStatusProviderUI = vpnStatusProviderUi,
            dontShowAgainStore = mockDontShowAgainStore,
            savedStateHandle = savedStateHandle,
        )

        userSettingsManager = CurrentUserLocalSettingsManager(
            userSettingsStoreProvider = LocalUserSettingsStoreProvider(
                factory = InMemoryDataStoreFactory(),
            )
        )

        viewModel = TvSettingsCustomDnsViewModel(
            savedStateHandle = savedStateHandle,
            isPrivateDnsActiveFlow = IsPrivateDnsActiveFlow(flow = isPrivateDnsActiveFlow),
            mainScope = testScope.backgroundScope,
            settingsReconnectHandler = settingsReconnectHandler,
            userSettingsManager = userSettingsManager,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `GIVEN user has private DNS enabled WHEN observing view state THEN view state is PrivateDnsConflict`() = testScope.runTest {
        isPrivateDnsActiveFlow.value = true
        val expectedViewState = TvSettingsCustomDnsViewModel.ViewState.PrivateDnsConflict(
            areCustomDnsSettingsChanged = false,
        )

        viewModel.viewStateFlow.test {
            val viewState = awaitItem()

            assertEquals(expectedViewState, viewState)
        }
    }

    @Test
    fun `GIVEN user has no custom DNS WHEN observing view state THEN view state is Empty`() = testScope.runTest {
        val customDnsList = emptyList<String>()
        val expectedViewState = TvSettingsCustomDnsViewModel.ViewState.Empty(
            areCustomDnsSettingsChanged = false,
        )
        userSettingsManager.updateCustomDnsList(customDnsList)

        viewModel.viewStateFlow.test {
            val viewState = awaitItem()

            assertEquals(expectedViewState, viewState)
        }
    }

    @Test
    fun `GIVEN user has custom DNS WHEN observing view state THEN view state is CustomDns`() = testScope.runTest {
        val customDnsList = listOf("1.1.1.1")
        val expectedViewState = TvSettingsCustomDnsViewModel.ViewState.CustomDns(
            areCustomDnsSettingsChanged = false,
            isCustomDnsEnabled = false,
            customDnsList = customDnsList,
            selectedCustomDns = null,
        )
        userSettingsManager.updateCustomDnsList(customDnsList)

        viewModel.viewStateFlow.test {
            val viewState = awaitItem()

            assertEquals(expectedViewState, viewState)
        }
    }

    @Test
    fun `GIVEN user has custom DNS WHEN toggle custom DNS THEN view state is updated`() = testScope.runTest {
        val customDnsList = listOf("2001:db8:3333:4444:5555:6666:7777:8888")
        val expectedViewStateDisabled = TvSettingsCustomDnsViewModel.ViewState.CustomDns(
            areCustomDnsSettingsChanged = false,
            isCustomDnsEnabled = false,
            customDnsList = customDnsList,
            selectedCustomDns = null,
        )
        val expectedViewStateEnabled = TvSettingsCustomDnsViewModel.ViewState.CustomDns(
            areCustomDnsSettingsChanged = true,
            isCustomDnsEnabled = true,
            customDnsList = customDnsList,
            selectedCustomDns = null,
        )
        userSettingsManager.updateCustomDnsList(customDnsList)

        viewModel.viewStateFlow.test {
            val viewStateDisabled = awaitItem()
            assertEquals(expectedViewStateDisabled, viewStateDisabled)

            viewModel.onToggleIsCustomDnsEnabled()

            val viewStateEnabled = awaitItem()
            assertEquals(expectedViewStateEnabled, viewStateEnabled)
        }
    }

    @Test
    fun `GIVEN user has custom DNS WHEN selecting custom DNS THEN view state is updated`() = testScope.runTest {
        val customDnsList = listOf("192.168.1.0")
        val selectedCustomDns = TvSettingsCustomDnsViewModel.SelectedCustomDns(
            index = 0,
            customDns = customDnsList[0],
        )
        val expectedViewStateUnselected = TvSettingsCustomDnsViewModel.ViewState.CustomDns(
            areCustomDnsSettingsChanged = false,
            isCustomDnsEnabled = false,
            customDnsList = customDnsList,
            selectedCustomDns = null,
        )
        val expectedViewStateSelected = TvSettingsCustomDnsViewModel.ViewState.CustomDns(
            areCustomDnsSettingsChanged = false,
            isCustomDnsEnabled = false,
            customDnsList = customDnsList,
            selectedCustomDns = selectedCustomDns,
        )
        userSettingsManager.updateCustomDnsList(customDnsList)

        viewModel.viewStateFlow.test {
            val viewStateUnselected = awaitItem()
            assertEquals(expectedViewStateUnselected, viewStateUnselected)

            viewModel.onCustomDnsSelected(
                index = selectedCustomDns.index,
                customDns = selectedCustomDns.customDns,
            )

            val viewStateSelected = awaitItem()
            assertEquals(expectedViewStateSelected, viewStateSelected)
        }
    }

    @Test
    fun `GIVEN user has custom DNS WHEN moving custom DNS to top THEN view state is updated`() = testScope.runTest {
        val customDnsList = listOf("1.1.1.1", "2.2.2.2", "3.3.3.3")
        val selectedCustomDns = TvSettingsCustomDnsViewModel.SelectedCustomDns(
            index = 2,
            customDns = customDnsList[2],
        )
        val expectedViewState = TvSettingsCustomDnsViewModel.ViewState.CustomDns(
            areCustomDnsSettingsChanged = true,
            isCustomDnsEnabled = false,
            customDnsList = listOf("3.3.3.3", "1.1.1.1", "2.2.2.2"),
            selectedCustomDns = null,
        )
        userSettingsManager.updateCustomDnsList(customDnsList)

        viewModel.viewStateFlow.test {
            skipItems(count = 1)

            viewModel.onMoveCustomDnsToTop(selectedCustomDns = selectedCustomDns)

            val viewStateSelected = awaitItem()
            assertEquals(expectedViewState, viewStateSelected)
        }
    }

    @Test
    fun `GIVEN user has custom DNS WHEN moving custom DNS up THEN view state is updated`() = testScope.runTest {
        val customDnsList = listOf("1.1.1.1", "2.2.2.2", "3.3.3.3")
        val selectedCustomDns = TvSettingsCustomDnsViewModel.SelectedCustomDns(
            index = 1,
            customDns = customDnsList[1],
        )
        val expectedViewState = TvSettingsCustomDnsViewModel.ViewState.CustomDns(
            areCustomDnsSettingsChanged = true,
            isCustomDnsEnabled = false,
            customDnsList = listOf("2.2.2.2", "1.1.1.1", "3.3.3.3"),
            selectedCustomDns = null,
        )
        userSettingsManager.updateCustomDnsList(customDnsList)

        viewModel.viewStateFlow.test {
            skipItems(count = 1)

            viewModel.onMoveCustomDnsUp(selectedCustomDns = selectedCustomDns)

            val viewStateSelected = awaitItem()
            assertEquals(expectedViewState, viewStateSelected)
        }
    }

    @Test
    fun `GIVEN user has custom DNS WHEN moving custom DNS down THEN view state is updated`() = testScope.runTest {
        val customDnsList = listOf("1.1.1.1", "2.2.2.2", "3.3.3.3")
        val selectedCustomDns = TvSettingsCustomDnsViewModel.SelectedCustomDns(
            index = 1,
            customDns = customDnsList[1],
        )
        val expectedViewState = TvSettingsCustomDnsViewModel.ViewState.CustomDns(
            areCustomDnsSettingsChanged = true,
            isCustomDnsEnabled = false,
            customDnsList = listOf("1.1.1.1", "3.3.3.3", "2.2.2.2"),
            selectedCustomDns = null,
        )
        userSettingsManager.updateCustomDnsList(customDnsList)

        viewModel.viewStateFlow.test {
            skipItems(count = 1)

            viewModel.onMoveCustomDnsDown(selectedCustomDns = selectedCustomDns)

            val viewStateSelected = awaitItem()
            assertEquals(expectedViewState, viewStateSelected)
        }
    }

    @Test
    fun `GIVEN user has custom DNS WHEN deleting custom DNS THEN view state is updated`() = testScope.runTest {
        listOf(
            listOf("1.1.1.1") to TvSettingsCustomDnsViewModel.ViewState.Empty(
                areCustomDnsSettingsChanged = true,
            ),
            listOf("1.1.1.1", "2.2.2.2") to TvSettingsCustomDnsViewModel.ViewState.CustomDns(
                areCustomDnsSettingsChanged = true,
                isCustomDnsEnabled = true,
                customDnsList = listOf("2.2.2.2"),
                selectedCustomDns = null,
            ),
        ).forEach { (customDnsList, expectedViewState) ->
            val selectedCustomDns = TvSettingsCustomDnsViewModel.SelectedCustomDns(
                index = 0,
                customDns = customDnsList[0],
            )
            userSettingsManager.updateCustomDnsList(customDnsList)

            viewModel.viewStateFlow.test {
                skipItems(count = 1)

                viewModel.onDeleteCustomDns(selectedCustomDns = selectedCustomDns)

                val viewStateSelected = awaitItem()
                assertEquals(expectedViewState, viewStateSelected)
            }
        }
    }

    @Test
    fun `GIVEN user has custom DNS WHEN showing delete custom DNS dialog THEN dialog state is updated`() = testScope.runTest {
        val customDnsList = listOf("1.1.1.1")
        val selectedCustomDns = TvSettingsCustomDnsViewModel.SelectedCustomDns(
            index = 0,
            customDns = customDnsList[0],
        )
        val expectedViewState = TvSettingsCustomDnsViewModel.DialogState.Delete(
            index = selectedCustomDns.index,
            customDns = selectedCustomDns.customDns,
        )

        viewModel.onShowDeleteCustomDnsDialog(selectedCustomDns = selectedCustomDns)

        viewModel.dialogStateFlow.test {
            val dialogState = awaitItem()

            assertEquals(expectedViewState, dialogState)
        }
    }

    @Test
    fun `WHEN dismissing delete custom DNS dialog THEN dialog state is updated`() = testScope.runTest {
        viewModel.onDismissDeleteCustomDnsDialog()

        viewModel.dialogStateFlow.test {
            val dialogState = awaitItem()

            assertNull(dialogState)
        }
    }

    @Test
    fun `WHEN showing reconnect now dialog THEN close screen event is emitted`() = testScope.runTest {
        val expectedEvent = TvSettingsCustomDnsViewModel.Event.OnClose
        coEvery {
            mockDontShowAgainStore.getChoice(type = DontShowAgainStore.Type.DnsChangeWhenConnected)
        } returns DontShowAgainStore.Choice.ShowDialog

        viewModel.onShowReconnectNowDialog(vpnUiDelegate = vpnUiDelegate)

        viewModel.eventChannelReceiver.receiveAsFlow().test {
            val event = awaitItem()

            assertEquals(expectedEvent, event)
        }
    }

    @Test
    fun `WHEN dismissing reconnect now dialog THEN close screen event is emitted`() = testScope.runTest {
        val expectedEvent = TvSettingsCustomDnsViewModel.Event.OnClose
        coEvery {
            mockDontShowAgainStore.getChoice(type = DontShowAgainStore.Type.DnsChangeWhenConnected)
        } returns DontShowAgainStore.Choice.ShowDialog

        viewModel.onDismissReconnectNowDialog()

        viewModel.eventChannelReceiver.receiveAsFlow().test {
            val event = awaitItem()

            assertEquals(expectedEvent, event)
        }
    }

}
