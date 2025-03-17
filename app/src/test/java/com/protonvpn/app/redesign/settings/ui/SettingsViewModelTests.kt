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

package com.protonvpn.app.redesign.settings.ui

import androidx.annotation.StringRes
import com.protonvpn.android.R
import com.protonvpn.android.appconfig.AppFeaturesPrefs
import com.protonvpn.android.appconfig.ChangeServerConfig
import com.protonvpn.android.appconfig.FeatureFlags
import com.protonvpn.android.appconfig.GetFeatureFlags
import com.protonvpn.android.appconfig.Restrictions
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.components.InstalledAppsProvider
import com.protonvpn.android.managed.ManagedConfig
import com.protonvpn.android.netshield.NetShieldProtocol
import com.protonvpn.android.profiles.data.ProfilesDao
import com.protonvpn.android.redesign.recents.usecases.RecentsManager
import com.protonvpn.android.redesign.settings.ui.SettingValue
import com.protonvpn.android.redesign.settings.ui.SettingsViewModel
import com.protonvpn.android.redesign.vpn.ui.GetConnectIntentViewState
import com.protonvpn.android.redesign.vpn.usecases.SettingsForConnection
import com.protonvpn.android.settings.data.CurrentUserLocalSettingsManager
import com.protonvpn.android.settings.data.EffectiveCurrentUserSettings
import com.protonvpn.android.settings.data.EffectiveCurrentUserSettingsFlow
import com.protonvpn.android.settings.data.LocalUserSettingsStoreProvider
import com.protonvpn.android.tv.IsTvCheck
import com.protonvpn.android.ui.settings.AppIconManager
import com.protonvpn.android.ui.settings.BuildConfigInfo
import com.protonvpn.android.utils.Constants
import com.protonvpn.android.vpn.VpnStateMonitor
import com.protonvpn.android.vpn.VpnStatusProviderUI
import com.protonvpn.android.vpn.usecases.FakeIsIPv6FeatureFlagEnabled
import com.protonvpn.android.widget.WidgetManager
import com.protonvpn.mocks.FakeIsCustomDnsEnabled
import com.protonvpn.test.shared.InMemoryDataStoreFactory
import com.protonvpn.test.shared.MockSharedPreferencesProvider
import com.protonvpn.test.shared.TestCurrentUserProvider
import com.protonvpn.test.shared.TestUser
import com.protonvpn.test.shared.createAccountUser
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.RelaxedMockK
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import me.proton.core.auth.domain.feature.IsFido2Enabled
import me.proton.core.usersettings.domain.usecase.ObserveRegisteredSecurityKeys
import me.proton.core.usersettings.domain.usecase.ObserveUserSettings
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTests {

    @RelaxedMockK
    private lateinit var mockBuildConfigInfo: BuildConfigInfo

    @MockK
    private lateinit var mockObserveUserSettings: ObserveUserSettings

    @MockK
    private lateinit var mockIsTvCheck: IsTvCheck
    @MockK
    private lateinit var mockAppIconManager: AppIconManager

    @RelaxedMockK
    private lateinit var mockInstalledAppsProvider: InstalledAppsProvider

    @RelaxedMockK
    private lateinit var mockGetQuickIntent: GetConnectIntentViewState
    @RelaxedMockK
    private lateinit var mockRecentManager: RecentsManager
    @RelaxedMockK
    private lateinit var isFido2Enabled: IsFido2Enabled
    @RelaxedMockK
    private lateinit var mockProfilesDao: ProfilesDao
    @RelaxedMockK
    private lateinit var observeRegisteredSecurityKeys: ObserveRegisteredSecurityKeys
    @RelaxedMockK
    private lateinit var mockWidgetManager: WidgetManager

    private lateinit var effectiveSettings: EffectiveCurrentUserSettings
    private lateinit var settingsForConnection: SettingsForConnection
    private lateinit var settingsManager: CurrentUserLocalSettingsManager
    private lateinit var testScope: TestScope
    private lateinit var testUserProvider: TestCurrentUserProvider

    private lateinit var settingsViewModel: SettingsViewModel
    private lateinit var vpnStateMonitor: VpnStateMonitor
    private lateinit var prefs: AppFeaturesPrefs

    private val businessEssentialUser = TestUser.businessEssential.vpnUser
    private val freeUser = TestUser.freeUser.vpnUser
    private val plusUser = TestUser.plusUser.vpnUser

    @Before
    fun setup() {
        MockKAnnotations.init(this)

        val testDispatcher = UnconfinedTestDispatcher()
        Dispatchers.setMain(testDispatcher)
        testScope = TestScope(testDispatcher)
        prefs = AppFeaturesPrefs(MockSharedPreferencesProvider())
        every { mockIsTvCheck.invoke() } returns false
        coEvery { mockRecentManager.getDefaultConnectionFlow() } returns flowOf(Constants.DEFAULT_CONNECTION)
        val accountUser = createAccountUser()
        testUserProvider = TestCurrentUserProvider(plusUser, accountUser)
        val currentUser = CurrentUser(testUserProvider)
        val changeServerConfig = ChangeServerConfig(30, 3, 60)
        val restrictionsFlow = currentUser.vpnUserFlow.mapLatest { vpnUser ->
            Restrictions(
                restrict = vpnUser?.isFreeUser == true,
                changeServerConfig
            )
        }
        val getFeatureFlags = GetFeatureFlags(MutableStateFlow(FeatureFlags()))
        val isIPv6FeatureFlagEnabled = FakeIsIPv6FeatureFlagEnabled(true)
        val isCustomDnsEnabled = FakeIsCustomDnsEnabled(true)
        settingsManager = CurrentUserLocalSettingsManager(
            LocalUserSettingsStoreProvider(InMemoryDataStoreFactory()),
        )
        val effectiveCurrentUserSettingsFlow = EffectiveCurrentUserSettingsFlow(
            settingsManager.rawCurrentUserSettingsFlow, getFeatureFlags, currentUser, mockIsTvCheck, restrictionsFlow, isIPv6FeatureFlagEnabled, isCustomDnsEnabled
        )
        effectiveSettings = EffectiveCurrentUserSettings(
            testScope.backgroundScope, effectiveCurrentUserSettingsFlow
        )
        vpnStateMonitor = VpnStateMonitor()
        val vpnStatusProviderUI = VpnStatusProviderUI(testScope.backgroundScope, vpnStateMonitor)
        settingsForConnection = SettingsForConnection(effectiveSettings, mockProfilesDao, vpnStatusProviderUI)

        settingsViewModel = SettingsViewModel(
            currentUser,
            mockObserveUserSettings,
            mockBuildConfigInfo,
            settingsForConnection,
            vpnStatusProviderUI,
            mockRecentManager,
            mockInstalledAppsProvider,
            mockGetQuickIntent,
            mockAppIconManager,
            ManagedConfig(MutableStateFlow(null)),
            isFido2Enabled,
            observeRegisteredSecurityKeys,
            mockWidgetManager,
            prefs,
            isIPv6FeatureFlagEnabled,
            isCustomDnsEnabled
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `netshield enabled for plus users`() = testScope.runTest {
        settingsManager.update { it.copy(netShield = NetShieldProtocol.ENABLED_EXTENDED) }
        val netShieldState = settingsViewModel.viewState.first().netShield
        assertNotNull(netShieldState)
        assertCommonProperties(
            true, R.string.netshield_feature_name, SettingValue.SettingStringRes(R.string.netshield_state_on), false, netShieldState
        )
    }

    @Test
    fun `netshield disabled for plus users`() = testScope.runTest {
        settingsManager.update { it.copy(netShield = NetShieldProtocol.DISABLED) }
        val netShieldState = settingsViewModel.viewState.first().netShield
        assertNotNull(netShieldState)
        assertCommonProperties(
            false, R.string.netshield_feature_name, SettingValue.SettingStringRes(R.string.netshield_state_off), false, netShieldState
        )
    }

    @Test
    fun `netshield disabled for free users even if setting is on`() = testScope.runTest {
        settingsManager.update { it.copy(netShield = NetShieldProtocol.ENABLED_EXTENDED) }
        testUserProvider.vpnUser = freeUser
        val netShieldState = settingsViewModel.viewState.first().netShield
        assertNotNull(netShieldState)
        assertCommonProperties(
            false, R.string.netshield_feature_name, SettingValue.SettingStringRes(R.string.netshield_state_off), true, netShieldState
        )
    }

    @Test
    fun `netshield hidden for B2B-essentials users`() = testScope.runTest {
        settingsManager.update { it.copy(netShield = NetShieldProtocol.ENABLED_EXTENDED) }
        testUserProvider.vpnUser = businessEssentialUser
        val state = settingsViewModel.viewState.first()
        assertNull(state.netShield)
    }

    @Test
    fun `vpn accelerator off for free users`() = testScope.runTest {
        settingsManager.update { it.copy(vpnAccelerator = false) }
        testUserProvider.vpnUser = freeUser
        val state = settingsViewModel.viewState.first()
        assertCommonProperties(
            false, R.string.settings_vpn_accelerator_title, SettingValue.SettingStringRes(R.string.vpn_accelerator_state_off), true, state.vpnAccelerator
        )
    }

    @Test
    fun `vpn accelerator off for free users even when enabled`() = testScope.runTest {
        settingsManager.update { it.copy(vpnAccelerator = true) }
        testUserProvider.vpnUser = freeUser
        val state = settingsViewModel.viewState.first()
        assertCommonProperties(
            false, R.string.settings_vpn_accelerator_title, SettingValue.SettingStringRes(R.string.vpn_accelerator_state_off), true, state.vpnAccelerator
        )
    }

    @Test
    fun `vpn accelerator enabled for plus users`() = testScope.runTest {
        settingsManager.update { it.copy(vpnAccelerator = true) }
        testUserProvider.vpnUser = plusUser
        val state = settingsViewModel.viewState.first()
        assertCommonProperties(
            true, R.string.settings_vpn_accelerator_title, SettingValue.SettingStringRes(R.string.vpn_accelerator_state_on), false, state.vpnAccelerator
        )
    }

    @Test
    fun `vpn accelerator disabled for plus users`() = testScope.runTest {
        settingsManager.update { it.copy(vpnAccelerator = false) }
        testUserProvider.vpnUser = plusUser
        val state = settingsViewModel.viewState.first()
        assertCommonProperties(
            false, R.string.settings_vpn_accelerator_title,
            SettingValue.SettingStringRes(R.string.vpn_accelerator_state_off),
            false,
            state.vpnAccelerator
        )
    }

    @Suppress("LongParameterList")
    private fun <T> assertCommonProperties(
        expectedValue: T,
        @StringRes expectedTitle: Int,
        expectedSettingValue: SettingValue,
        expectedIsRestricted: Boolean,
        settingState: SettingsViewModel.SettingViewState<T>
    ) {
        assertEquals(expectedValue, settingState.value)
        assertEquals(expectedTitle, settingState.titleRes)
        assertEquals(expectedSettingValue, settingState.settingValueView)
        assertEquals(expectedIsRestricted, settingState.isRestricted)
    }
}
