/*
 * Copyright (c) 2025. Proton AG
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

package com.protonvpn.app.vpn

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.protonvpn.android.settings.data.CurrentUserLocalSettingsManager
import com.protonvpn.android.settings.data.LocalUserSettingsStoreProvider
import com.protonvpn.android.settings.data.SplitTunnelingMode
import com.protonvpn.android.ui.settings.LabeledItem
import com.protonvpn.android.ui.settings.SettingsSplitTunnelIpsActivity
import com.protonvpn.android.ui.settings.SettingsSplitTunnelIpsViewModel
import com.protonvpn.android.vpn.usecases.FakeIsIPv6FeatureFlagEnabled
import com.protonvpn.test.shared.InMemoryDataStoreFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsSplitTunnelIpsViewModelTests {

    private lateinit var testScope: TestScope

    @Before
    fun setup() {
        val dispatcher = StandardTestDispatcher()
        testScope = TestScope(dispatcher)
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testValidIPs() = testScope.runTest {
        val viewModel = createViewModel()
        assertTrue(viewModel.isValidIp("1.1.1.1"))
        assertTrue(viewModel.isValidIp("2000::"))
        assertTrue(viewModel.isValidIp("::1"))
        assertFalse(viewModel.isValidIp("1.1"))
        assertFalse(viewModel.isValidIp("1.1.1.1/24")) // Don't allow ranges for now
        assertFalse(viewModel.isValidIp("2000::/8"))
        assertFalse(viewModel.isValidIp(""))
    }

    @Test
    fun testAddAddress() = testScope.runTest {
        val viewModel = createViewModel()
        viewModel.addAddress("1.1.1.1")
        viewModel.addAddress("2000::")
        assertEquals(
            SettingsSplitTunnelIpsViewModel.State(
                listOf("1.1.1.1".let { LabeledItem(it, it) }, "2000::".let { LabeledItem(it, it) }),
                showHelp = true
            ),
            viewModel.state.first()
        )
    }

    @Test
    fun `don't show help when IPv6 ff disabled`() = testScope.runTest {
        val viewModel = createViewModel(v6FeatureFlagEnabled = false)
        assertEquals(SettingsSplitTunnelIpsViewModel.State(emptyList(), showHelp = false), viewModel.state.first())
    }

    @Test
    fun `should display IPv6 dialog`() = testScope.runTest {
        val viewModel = createViewModel(
            v6FeatureFlagEnabled = true, v6SettingEnabled = false, mode = SplitTunnelingMode.INCLUDE_ONLY)
        viewModel.events.test {
            viewModel.addAddress("2000::")
            awaitItem() == SettingsSplitTunnelIpsViewModel.Event.ShowIPv6EnableSettingDialog
            viewModel.onEnableIPv6()
            awaitItem() == SettingsSplitTunnelIpsViewModel.Event.ShowIPv6EnabledToast
        }
    }

    @Test
    fun `don't display IPv6 dialog on exclude mode`() = testScope.runTest {
        val viewModel = createViewModel(
            v6FeatureFlagEnabled = true, v6SettingEnabled = false, mode = SplitTunnelingMode.EXCLUDE_ONLY)
        viewModel.events.test {
            viewModel.addAddress("2000::")
            expectNoEvents()
        }
    }

    @Test
    fun `don't display IPv6 dialog if feature flag is disabled`() = testScope.runTest {
        val viewModel = createViewModel(
            v6FeatureFlagEnabled = false, v6SettingEnabled = false, mode = SplitTunnelingMode.EXCLUDE_ONLY)
        viewModel.events.test {
            viewModel.addAddress("2000::")
            expectNoEvents()
        }
    }

    private suspend fun createViewModel(
        v6FeatureFlagEnabled: Boolean = true,
        v6SettingEnabled: Boolean = true,
        mode: SplitTunnelingMode = SplitTunnelingMode.EXCLUDE_ONLY
    ) = SettingsSplitTunnelIpsViewModel(
        mainScope = testScope.backgroundScope,
        userSettingsManager =
            CurrentUserLocalSettingsManager(LocalUserSettingsStoreProvider(InMemoryDataStoreFactory())).apply {
                update { it.copy(ipV6Enabled = v6SettingEnabled) }
            },
        isIPv6FeatureFlagEnabled = FakeIsIPv6FeatureFlagEnabled(v6FeatureFlagEnabled),
        SavedStateHandle(
            mapOf(SettingsSplitTunnelIpsActivity.SPLIT_TUNNELING_MODE_KEY to mode))
    )
}
