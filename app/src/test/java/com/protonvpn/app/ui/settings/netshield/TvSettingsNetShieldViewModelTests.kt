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

package com.protonvpn.app.ui.settings.netshield

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.protonvpn.android.redesign.settings.ui.SettingsReconnectHandler
import com.protonvpn.android.settings.data.CurrentUserLocalSettingsManager
import com.protonvpn.android.settings.data.LocalUserSettingsStoreProvider
import com.protonvpn.android.tv.settings.netshield.TvSettingsNetShieldViewModel
import com.protonvpn.android.userstorage.DontShowAgainStore
import com.protonvpn.android.vpn.DnsOverride
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
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TvSettingsNetShieldViewModelTests {

    @MockK
    private lateinit var mockDontShowAgainStore: DontShowAgainStore

    @MockK
    private lateinit var mockVpnConnectionManager: VpnConnectionManager

    private lateinit var isPrivateDnsActiveFlow: MutableStateFlow<Boolean>

    private lateinit var testScope: TestScope

    private lateinit var userSettingsManager: CurrentUserLocalSettingsManager

    private lateinit var vpnStateMonitor: VpnStateMonitor

    private lateinit var vpnUiDelegate: VpnUiDelegate

    private lateinit var viewModel: TvSettingsNetShieldViewModel

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

        viewModel = TvSettingsNetShieldViewModel(
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
    fun `GIVEN user has NetShield enabled AND no custom DNS AND no private DNS WHEN observing view state THEN view state is emitted`() = testScope.runTest {
        isPrivateDnsActiveFlow.value = false
        userSettingsManager.updateCustomDns { customDns -> customDns.copy(toggleEnabled = false) }
        val expectedViewState = TvSettingsNetShieldViewModel.ViewState(
            isNetShieldEnabled = true,
            dnsOverride = DnsOverride.None,
        )

        viewModel.viewState.test {
            val viewState = awaitItem()

            assertEquals(expectedViewState, viewState)
        }
    }

    @Test
    fun `GIVEN user has NetShield enabled AND custom DNS enabled AND no private DNS WHEN observing view state THEN view state is emitted`() = testScope.runTest {
        isPrivateDnsActiveFlow.value = false
        userSettingsManager.updateCustomDns { customDns ->
            customDns.copy(
                toggleEnabled = true,
                rawDnsList = listOf("8.8.8.8"),
            )
        }
        val expectedViewState = TvSettingsNetShieldViewModel.ViewState(
            isNetShieldEnabled = true,
            dnsOverride = DnsOverride.CustomDns,
        )

        viewModel.viewState.test {
            val viewState = awaitItem()

            assertEquals(expectedViewState, viewState)
        }
    }

    @Test
    fun `GIVEN user has NetShield enabled AND no custom AND private DNS enabled WHEN observing view state THEN view state is emitted`() = testScope.runTest {
        isPrivateDnsActiveFlow.value = true
        userSettingsManager.updateCustomDns { customDns -> customDns.copy(toggleEnabled = false) }
        val expectedViewState = TvSettingsNetShieldViewModel.ViewState(
            isNetShieldEnabled = true,
            dnsOverride = DnsOverride.SystemPrivateDns,
        )

        viewModel.viewState.test {
            val viewState = awaitItem()

            assertEquals(expectedViewState, viewState)
        }
    }

    @Test
    fun `GIVEN user toggles NetShield WHEN observing view state THEN view state is updated`() = testScope.runTest {
        listOf(
            true to false,
            false to true,
        ).forEach { (isNetShieldEnabled, expectedIsNetShieldEnabled) ->
            userSettingsManager.updateCustomDns { customDns ->
                customDns.copy(toggleEnabled = isNetShieldEnabled)
            }
            val expectedViewState = TvSettingsNetShieldViewModel.ViewState(
                isNetShieldEnabled = expectedIsNetShieldEnabled,
                dnsOverride = DnsOverride.None,
            )

            viewModel.toggleNetShield()

            viewModel.viewState.test {
                val viewState = awaitItem()

                assertEquals(expectedViewState, viewState)
            }
        }
    }

    @Test
    fun `GIVEN user has custom DNS conflict WHEN disabling custom DNS THEN view state is updated solving conflict`() = testScope.runTest {
        userSettingsManager.updateCustomDns { customDns ->
            customDns.copy(
                toggleEnabled = true,
                rawDnsList = listOf("255.255.255.255"),
            )
        }

        val expectedConflictViewState = TvSettingsNetShieldViewModel.ViewState(
            isNetShieldEnabled = true,
            dnsOverride = DnsOverride.CustomDns,
        )

        val expectedNoConflictViewState = TvSettingsNetShieldViewModel.ViewState(
            isNetShieldEnabled = true,
            dnsOverride = DnsOverride.None,
        )

        viewModel.viewState.test {
            val conflictViewState = awaitItem()
            assertEquals(expectedConflictViewState, conflictViewState)

            viewModel.disableCustomDns()

            val noConflictViewState = awaitItem()
            assertEquals(expectedNoConflictViewState, noConflictViewState)
        }
    }

    @Test
    fun `GIVEN custom DNS conflict WHEN disabling custom DNS THEN show reconnect now dialog event is emitted`() = testScope.runTest {
        val expectedEvent = TvSettingsNetShieldViewModel.Event.OnShowReconnectNowDialog
        userSettingsManager.updateCustomDns { customDns ->
            customDns.copy(
                toggleEnabled = true,
                rawDnsList = listOf("0.0.0.0"),
            )
        }

        viewModel.disableCustomDns()

        viewModel.eventChannelReceiver.receiveAsFlow().test {
            val event = awaitItem()

            assertEquals(expectedEvent, event)
        }
    }

    @Test
    fun `WHEN reconnecting to VPN THEN close screen event is emitted`() = testScope.runTest {
        val expectedEvent = TvSettingsNetShieldViewModel.Event.OnClose
        coEvery {
            mockDontShowAgainStore.getChoice(type = DontShowAgainStore.Type.DnsChangeWhenConnected)
        } returns DontShowAgainStore.Choice.ShowDialog

        viewModel.onReconnectNow(vpnUiDelegate = vpnUiDelegate)

        viewModel.eventChannelReceiver.receiveAsFlow().test {
            val event = awaitItem()

            assertEquals(expectedEvent, event)
        }
    }

    @Test
    fun `WHEN dismissing reconnect now dialog THEN dismiss event is emitted`() = testScope.runTest {
        val expectedEvent = TvSettingsNetShieldViewModel.Event.OnDismissReconnectNowDialog
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
