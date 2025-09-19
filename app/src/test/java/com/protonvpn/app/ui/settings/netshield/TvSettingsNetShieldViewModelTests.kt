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

import app.cash.turbine.test
import com.protonvpn.android.settings.data.CurrentUserLocalSettingsManager
import com.protonvpn.android.settings.data.LocalUserSettingsStoreProvider
import com.protonvpn.android.tv.settings.netshield.TvSettingsNetShieldViewModel
import com.protonvpn.android.vpn.DnsOverride
import com.protonvpn.android.vpn.IsPrivateDnsActiveFlow
import com.protonvpn.test.shared.InMemoryDataStoreFactory
import junit.framework.Assert.assertEquals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TvSettingsNetShieldViewModelTests {

    private lateinit var isPrivateDnsActiveFlow: MutableStateFlow<Boolean>

    private lateinit var testScope: TestScope

    private lateinit var userSettingsManager: CurrentUserLocalSettingsManager

    private lateinit var viewModel: TvSettingsNetShieldViewModel

    @Before
    fun setup() {
        val testDispatcher = UnconfinedTestDispatcher(scheduler = TestCoroutineScheduler())

        testScope = TestScope(context = testDispatcher)

        Dispatchers.setMain(dispatcher = testDispatcher)

        isPrivateDnsActiveFlow = MutableStateFlow(value = false)

        userSettingsManager = CurrentUserLocalSettingsManager(
            userSettingsStoreProvider = LocalUserSettingsStoreProvider(
                factory = InMemoryDataStoreFactory(),
            )
        )

        viewModel = TvSettingsNetShieldViewModel(
            isPrivateDnsActiveFlow = IsPrivateDnsActiveFlow(flow = isPrivateDnsActiveFlow),
            mainScope = testScope.backgroundScope,
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

}
