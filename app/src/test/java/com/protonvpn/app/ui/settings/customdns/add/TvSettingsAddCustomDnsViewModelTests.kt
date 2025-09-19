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

package com.protonvpn.app.ui.settings.customdns.add

import app.cash.turbine.test
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.redesign.settings.ui.customdns.AddDnsError
import com.protonvpn.android.redesign.vpn.usecases.SettingsForConnection
import com.protonvpn.android.settings.data.ApplyEffectiveUserSettings
import com.protonvpn.android.settings.data.CurrentUserLocalSettingsManager
import com.protonvpn.android.settings.data.LocalUserSettingsStoreProvider
import com.protonvpn.android.settings.data.SettingsFeatureFlagsFlow
import com.protonvpn.android.tv.IsTvCheck
import com.protonvpn.android.tv.settings.FakeIsTvAutoConnectFeatureFlagEnabled
import com.protonvpn.android.tv.settings.FakeIsTvCustomDnsSettingFeatureFlagEnabled
import com.protonvpn.android.tv.settings.FakeIsTvNetShieldSettingFeatureFlagEnabled
import com.protonvpn.android.tv.settings.customdns.add.TvSettingsAddCustomDnsViewModel
import com.protonvpn.android.vpn.VpnStateMonitor
import com.protonvpn.android.vpn.VpnStatusProviderUI
import com.protonvpn.android.vpn.usecases.FakeIsIPv6FeatureFlagEnabled
import com.protonvpn.mocks.FakeGetProfileById
import com.protonvpn.mocks.FakeIsLanDirectConnectionsFeatureFlagEnabled
import com.protonvpn.test.shared.InMemoryDataStoreFactory
import com.protonvpn.test.shared.TestCurrentUserProvider
import com.protonvpn.test.shared.TestUser
import com.protonvpn.test.shared.createAccountUser
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

@OptIn(ExperimentalCoroutinesApi::class)
class TvSettingsAddCustomDnsViewModelTests {

    @MockK
    private lateinit var mockIsTvCheck: IsTvCheck

    private lateinit var testScope: TestScope

    private lateinit var settingsForConnection: SettingsForConnection

    private lateinit var userSettingsManager: CurrentUserLocalSettingsManager

    private lateinit var viewModel: TvSettingsAddCustomDnsViewModel

    @Before
    fun setup() {
        MockKAnnotations.init(this)

        val testDispatcher = UnconfinedTestDispatcher(scheduler = TestCoroutineScheduler())

        testScope = TestScope(context = testDispatcher)

        Dispatchers.setMain(dispatcher = testDispatcher)

        every { mockIsTvCheck.invoke() } returns true

        userSettingsManager = CurrentUserLocalSettingsManager(
            userSettingsStoreProvider = LocalUserSettingsStoreProvider(
                factory = InMemoryDataStoreFactory(),
            )
        )

        settingsForConnection = SettingsForConnection(
            settingsManager = userSettingsManager,
            getProfileById = FakeGetProfileById(),
            applyEffectiveUserSettings = ApplyEffectiveUserSettings(
                mainScope = testScope.backgroundScope,
                currentUser = CurrentUser(
                    provider = TestCurrentUserProvider(
                        vpnUser = TestUser.plusUser.vpnUser,
                        user = createAccountUser(),
                    )
                ),
                isTv = mockIsTvCheck,
                flags = SettingsFeatureFlagsFlow(
                    isIPv6FeatureFlagEnabled = FakeIsIPv6FeatureFlagEnabled(true),
                    isDirectLanConnectionsFeatureFlagEnabled = FakeIsLanDirectConnectionsFeatureFlagEnabled(true),
                    isTvAutoConnectFeatureFlagEnabled = FakeIsTvAutoConnectFeatureFlagEnabled(true),
                    isTvNetShieldSettingFeatureFlagEnabled = FakeIsTvNetShieldSettingFeatureFlagEnabled(true),
                    isTvCustomDnsSettingFeatureFlagEnabled = FakeIsTvCustomDnsSettingFeatureFlagEnabled(true),
                )
            ),
            vpnStatusProviderUI = VpnStatusProviderUI(
                scope = testScope.backgroundScope,
                monitor = VpnStateMonitor(),
            ),
        )

        viewModel = TvSettingsAddCustomDnsViewModel(
            mainScope = testScope,
            settingsForConnection = settingsForConnection,
            userSettingsManager = userSettingsManager,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `WHEN observing view state THEN initial view state is emitted`() = testScope.runTest {
        val expectedViewState = TvSettingsAddCustomDnsViewModel.ViewState(error = null)

        viewModel.viewStateFlow.test {
            val viewState = awaitItem()

            assertEquals(expectedViewState, viewState)
        }
    }

    @Test
    fun `GIVEN empty custom DNS WHEN observing view state THEN view state error is EmptyInput`() = testScope.runTest {
        val customDns = ""
        val expectedViewState = TvSettingsAddCustomDnsViewModel.ViewState(error = AddDnsError.EmptyInput)

        viewModel.onAddCustomDns(newCustomDns = customDns)

        viewModel.viewStateFlow.test {
            val viewState = awaitItem()

            assertEquals(expectedViewState, viewState)
        }
    }

    @Test
    fun `GIVEN invalid custom DNS WHEN observing view state THEN view state error is InvalidInput`() = testScope.runTest {
        val customDns = "invalid DNS"
        val expectedViewState = TvSettingsAddCustomDnsViewModel.ViewState(error = AddDnsError.InvalidInput)

        viewModel.onAddCustomDns(newCustomDns = customDns)

        viewModel.viewStateFlow.test {
            val viewState = awaitItem()

            assertEquals(expectedViewState, viewState)
        }
    }

    @Test
    fun `GIVEN custom DNS already exists WHEN observing view state THEN view state error is DuplicateInput`() = testScope.runTest {
        val customDns = "1.1.1.1"
        val expectedViewState = TvSettingsAddCustomDnsViewModel.ViewState(error = AddDnsError.DuplicateInput)
        userSettingsManager.updateCustomDnsList(newDnsList = listOf(customDns))

        viewModel.onAddCustomDns(newCustomDns = customDns)

        viewModel.viewStateFlow.test {
            val viewState = awaitItem()

            assertEquals(expectedViewState, viewState)
        }
    }

    @Test
    fun `GIVEN custom DNS changes WHEN observing view state THEN view state error is reset`() = testScope.runTest {
        val customDns = ""
        val expectedViewState = TvSettingsAddCustomDnsViewModel.ViewState(error = null)
        viewModel.onAddCustomDns(newCustomDns = customDns)

        viewModel.onCustomDnsChanged()

        viewModel.viewStateFlow.test {
            val viewState = awaitItem()

            assertEquals(expectedViewState, viewState)
        }
    }

    @Test
    fun `GIVEN valid custom DNS WHEN adding the custom DNS THEN custom DNS is added`() = testScope.runTest {
        val customDns = "2001:db8:3333:4444:5555:6666:7777:8888"
        val expectedCustomDnsList = listOf(customDns)
        viewModel.onAddCustomDns(newCustomDns = customDns)

        userSettingsManager.rawCurrentUserSettingsFlow.test {
            val customDnsList = awaitItem().customDns.rawDnsList

            assertEquals(expectedCustomDnsList, customDnsList)
        }
    }

    @Test
    fun `GIVEN valid custom DNS AND does not have custom DNS WHEN adding the custom DNS THEN shows net shield conflict dialog`() = testScope.runTest {
        val customDns = "16.0.254.1"
        val expectedEvent = TvSettingsAddCustomDnsViewModel.Event.OnShowNetShieldConflictDialog

        viewModel.onAddCustomDns(newCustomDns = customDns)

        viewModel.eventChannelReceiver.receiveAsFlow().test {
            val event = awaitItem()

            assertEquals(expectedEvent, event)
        }
    }

    @Test
    fun `GIVEN valid custom DNS AND already has custom DNS WHEN adding the custom DNS THEN notifies custom DNS is added`() = testScope.runTest {
        val customDns = "16.0.254.1"
        val expectedEvent = TvSettingsAddCustomDnsViewModel.Event.OnCustomDnsAdded
        userSettingsManager.updateCustomDnsList(newDnsList = listOf("8.8.8.8"))

        viewModel.onAddCustomDns(newCustomDns = customDns)

        viewModel.eventChannelReceiver.receiveAsFlow().test {
            val event = awaitItem()

            assertEquals(expectedEvent, event)
        }
    }

}
