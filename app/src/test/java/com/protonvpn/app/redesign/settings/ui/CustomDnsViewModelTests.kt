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

package com.protonvpn.app.redesign.settings.ui

import app.cash.turbine.turbineScope
import com.protonvpn.android.api.DohEnabled
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.di.Distinct
import com.protonvpn.android.redesign.settings.ui.customdns.CustomDnsViewModel
import com.protonvpn.android.redesign.vpn.ui.GetConnectIntentViewState
import com.protonvpn.android.redesign.vpn.usecases.SettingsForConnection
import com.protonvpn.android.settings.data.CurrentUserLocalSettingsManager
import com.protonvpn.android.settings.data.CustomDnsSettings
import com.protonvpn.android.settings.data.EffectiveCurrentUserSettings
import com.protonvpn.android.vpn.DnsOverrideFlow
import com.protonvpn.android.vpn.VpnStatusProviderUI
import com.protonvpn.app.testRules.RobolectricHiltAndroidRule
import com.protonvpn.test.shared.TestCurrentUserProvider
import com.protonvpn.test.shared.TestUser
import com.protonvpn.test.shared.createAccountUser
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import javax.inject.Inject
import kotlin.test.assertNotNull

@OptIn(ExperimentalCoroutinesApi::class)
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
class CustomDnsViewModelTests {

    @get:Rule
    val hiltRule = RobolectricHiltAndroidRule(this)

    @Inject
    lateinit var currentUserProvider: TestCurrentUserProvider

    @Inject
    lateinit var dohEnabledProvider: DohEnabled.Provider // Provider is needed to unblock DohEnabled.

    @Inject
    lateinit var effectiveCurrentUserSettings: EffectiveCurrentUserSettings

    @Inject
    lateinit var userSettingsManager: CurrentUserLocalSettingsManager

    @Inject
    lateinit var viewModelInjector: CustomDnsViewModelInjector

    lateinit var viewModel: CustomDnsViewModel

    @Before
    fun setup() {
        hiltRule.inject()
        currentUserProvider.user = createAccountUser()
        currentUserProvider.vpnUser = TestUser.plusUser.vpnUser
        viewModel = viewModelInjector.createViewModel()

    }

    @Test
    fun `adding the first DNS item enables Custom DNS`() = runTest {
        turbineScope {
            val effectiveSettingsTurbine = effectiveCurrentUserSettings.effectiveSettings.testIn(backgroundScope)
            val viewStateTurbine = viewModel.customDnsHelper.customDnsSettingState.testIn(backgroundScope)
            val initialSettings = effectiveSettingsTurbine.expectMostRecentItem()
            assertEquals(CustomDnsSettings(toggleEnabled = false, rawDnsList = emptyList()), initialSettings.customDns)

            viewModel.addNewDns("1.2.3.4")
            runCurrent()
            val settings = effectiveSettingsTurbine.expectMostRecentItem()
            val viewState = viewStateTurbine.expectMostRecentItem()?.dnsViewState
            assertNotNull(viewState)
            assertTrue(settings.customDns.effectiveEnabled)
            assertTrue(viewState.value)
        }
    }

    @Test
    fun `disabling Custom DNS returns empty effective Custom DNS list`() = runTest {
        turbineScope {
            val effectiveSettingsTurbine = effectiveCurrentUserSettings.effectiveSettings.testIn(backgroundScope)
            val viewStateTurbine = viewModel.customDnsHelper.customDnsSettingState.testIn(backgroundScope)
            userSettingsManager.updateCustomDns {
                CustomDnsSettings(toggleEnabled = true, rawDnsList = listOf("1.2.3.4"))
            }

            viewModel.toggleCustomDns()
            runCurrent()
            val settings = effectiveSettingsTurbine.expectMostRecentItem()
            val viewState = viewStateTurbine.expectMostRecentItem()?.dnsViewState
            assertNotNull(viewState)
            assertEquals(emptyList<String>(), settings.customDns.effectiveDnsList)
            assertFalse(settings.customDns.effectiveEnabled)
            // Non-empty list causes the non-empty state to be shown - the logic is in composable so can't be tested
            // here.
            assertEquals(listOf("1.2.3.4"), viewState.customDns)
            assertFalse(viewState.value)
        }
    }

    @Test
    fun `disabling and reenabling Custom DNS retains its DNS list`() = runTest {
        turbineScope {
            val dnsList = listOf("1.2.3.4")
            val effectiveSettingsTurbine = effectiveCurrentUserSettings.effectiveSettings.testIn(backgroundScope)
            val viewStateTurbine = viewModel.customDnsHelper.customDnsSettingState.testIn(backgroundScope)
            userSettingsManager.updateCustomDns {
                CustomDnsSettings(toggleEnabled = true, rawDnsList = dnsList)
            }

            viewModel.toggleCustomDns()
            runCurrent()
            val intermediateSettings = effectiveSettingsTurbine.expectMostRecentItem()
            assertEquals(emptyList<String>(), intermediateSettings.customDns.effectiveDnsList)

            viewModel.toggleCustomDns()
            runCurrent()
            val settings = effectiveSettingsTurbine.expectMostRecentItem()
            val viewState = viewStateTurbine.expectMostRecentItem()?.dnsViewState
            assertNotNull(viewState)

            assertTrue(viewState.value)
            assertEquals(dnsList, settings.customDns.effectiveDnsList)
            assertEquals(dnsList, viewState.customDns)
        }
    }
}

// Hilt won't inject the view model, find a way to avoid these injectors.
@Distinct
class CustomDnsViewModelInjector @Inject constructor(
    private val mainScope: CoroutineScope,
    private val userSettingsManager: CurrentUserLocalSettingsManager,
    private val getConnectIntentViewState: GetConnectIntentViewState,
    private val settingsForConnection: SettingsForConnection,
    private val vpnStatusProviderUI: VpnStatusProviderUI,
    private val dnsOverrideFlow: DnsOverrideFlow,
    private val currentUser: CurrentUser,
) {
    fun createViewModel() = CustomDnsViewModel(
        mainScope, userSettingsManager, getConnectIntentViewState, settingsForConnection, vpnStatusProviderUI, dnsOverrideFlow, currentUser
    )
}
