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
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.components.InstalledAppsProvider
import com.protonvpn.android.managed.ManagedConfig
import com.protonvpn.android.models.vpn.ConnectionParams
import com.protonvpn.android.netshield.NetShieldProtocol
import com.protonvpn.android.profiles.data.toProfile
import com.protonvpn.android.redesign.CountryId
import com.protonvpn.android.redesign.recents.usecases.RecentsManager
import com.protonvpn.android.redesign.settings.ui.SettingValue
import com.protonvpn.android.redesign.settings.ui.SettingsViewModel
import com.protonvpn.android.redesign.settings.ui.SettingsViewModel.SettingViewState
import com.protonvpn.android.redesign.vpn.ConnectIntent
import com.protonvpn.android.redesign.vpn.ui.GetConnectIntentViewState
import com.protonvpn.android.redesign.vpn.usecases.SettingsForConnection
import com.protonvpn.android.settings.data.ApplyEffectiveUserSettings
import com.protonvpn.android.settings.data.CurrentUserLocalSettingsManager
import com.protonvpn.android.settings.data.CustomDnsSettings
import com.protonvpn.android.settings.data.EffectiveCurrentUserSettings
import com.protonvpn.android.settings.data.EffectiveCurrentUserSettingsFlow
import com.protonvpn.android.settings.data.LocalUserSettingsStoreProvider
import com.protonvpn.android.settings.data.SettingsFeatureFlagsFlow
import com.protonvpn.android.tv.IsTvCheck
import com.protonvpn.android.tv.settings.FakeIsTvAutoConnectFeatureFlagEnabled
import com.protonvpn.android.tv.settings.FakeIsTvCustomDnsSettingFeatureFlagEnabled
import com.protonvpn.android.tv.settings.FakeIsTvNetShieldSettingFeatureFlagEnabled
import com.protonvpn.android.ui.settings.AppIconManager
import com.protonvpn.android.ui.settings.BuildConfigInfo
import com.protonvpn.android.utils.Constants
import com.protonvpn.android.vpn.IsPrivateDnsActiveFlow
import com.protonvpn.android.vpn.VpnState
import com.protonvpn.android.vpn.VpnStateMonitor
import com.protonvpn.android.vpn.VpnStatusProviderUI
import com.protonvpn.android.vpn.usecases.FakeIsIPv6FeatureFlagEnabled
import com.protonvpn.android.widget.WidgetManager
import com.protonvpn.mocks.FakeGetProfileById
import com.protonvpn.mocks.FakeIsLanDirectConnectionsFeatureFlagEnabled
import com.protonvpn.test.shared.InMemoryDataStoreFactory
import com.protonvpn.test.shared.MockSharedPreferencesProvider
import com.protonvpn.test.shared.TestCurrentUserProvider
import com.protonvpn.test.shared.TestUser
import com.protonvpn.test.shared.createAccountUser
import com.protonvpn.test.shared.createProfileEntity
import com.protonvpn.test.shared.createServer
import com.protonvpn.test.shared.createSettingsOverrides
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
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
import kotlin.test.assertIs
import kotlin.test.assertNotNull

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
    private lateinit var mockRecentManager: RecentsManager
    @RelaxedMockK
    private lateinit var isFido2Enabled: IsFido2Enabled
    @RelaxedMockK
    private lateinit var observeRegisteredSecurityKeys: ObserveRegisteredSecurityKeys
    @RelaxedMockK
    private lateinit var mockWidgetManager: WidgetManager

    private lateinit var effectiveSettings: EffectiveCurrentUserSettings
    private lateinit var isPrivateDnsActive: MutableStateFlow<Boolean>
    private lateinit var getProfileById: FakeGetProfileById
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
        val isIPv6FeatureFlagEnabled = FakeIsIPv6FeatureFlagEnabled(true)
        val isDirectLanConnectionsFeatureFlagEnabled = FakeIsLanDirectConnectionsFeatureFlagEnabled(true)
        settingsManager = CurrentUserLocalSettingsManager(
            LocalUserSettingsStoreProvider(InMemoryDataStoreFactory()),
        )
        val applyEffectiveUserSettings = ApplyEffectiveUserSettings(
            mainScope = testScope.backgroundScope,
            currentUser = currentUser,
            isTv = mockIsTvCheck,
            flags = SettingsFeatureFlagsFlow(
                isIPv6FeatureFlagEnabled = isIPv6FeatureFlagEnabled,
                isDirectLanConnectionsFeatureFlagEnabled = isDirectLanConnectionsFeatureFlagEnabled,
                isTvAutoConnectFeatureFlagEnabled = FakeIsTvAutoConnectFeatureFlagEnabled(true),
                isTvNetShieldSettingFeatureFlagEnabled = FakeIsTvNetShieldSettingFeatureFlagEnabled(true),
                isTvCustomDnsSettingFeatureFlagEnabled = FakeIsTvCustomDnsSettingFeatureFlagEnabled(true),
            )
        )
        val effectiveCurrentUserSettingsFlow = EffectiveCurrentUserSettingsFlow(
            rawCurrentUserSettingsFlow = settingsManager.rawCurrentUserSettingsFlow,
            applyEffectiveUserSettings = applyEffectiveUserSettings,
        )
        effectiveSettings = EffectiveCurrentUserSettings(
            testScope.backgroundScope, effectiveCurrentUserSettingsFlow
        )
        vpnStateMonitor = VpnStateMonitor()
        val vpnStatusProviderUI = VpnStatusProviderUI(testScope.backgroundScope, vpnStateMonitor)
        getProfileById = FakeGetProfileById()
        settingsForConnection = SettingsForConnection(
            settingsManager = settingsManager,
            getProfileById = getProfileById,
            applyEffectiveUserSettings = applyEffectiveUserSettings,
            vpnStatusProviderUI = vpnStatusProviderUI,
        )

        val getConnectIntentViewState = GetConnectIntentViewState(
            serverManager = mockk(),
            translator = mockk(),
            getProfileById = getProfileById,
        )
        isPrivateDnsActive = MutableStateFlow(false)
        settingsViewModel = SettingsViewModel(
            currentUser = currentUser,
            accountUserSettings = mockObserveUserSettings,
            buildConfigInfo = mockBuildConfigInfo,
            settingsForConnection = settingsForConnection,
            recentsManager = mockRecentManager,
            installedAppsProvider = mockInstalledAppsProvider,
            getConnectIntentViewState = getConnectIntentViewState,
            appIconManager = mockAppIconManager,
            managedConfig = ManagedConfig(MutableStateFlow(null)),
            isFido2Enabled = isFido2Enabled,
            observeRegisteredSecurityKeys = observeRegisteredSecurityKeys,
            appWidgetManager = mockWidgetManager,
            appFeaturePrefs = prefs,
            isIPv6FeatureFlagEnabled = isIPv6FeatureFlagEnabled,
            isPrivateDnsActiveFlow = IsPrivateDnsActiveFlow(isPrivateDnsActive),
            isDirectLanConnectionsFeatureFlagEnabled = isDirectLanConnectionsFeatureFlagEnabled
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
    fun `GIVEN business essential user WHEN enabling NetShield THEN NetShield setting is on`() = testScope.runTest {
        testUserProvider.vpnUser = businessEssentialUser

        settingsManager.update { it.copy(netShield = NetShieldProtocol.ENABLED_EXTENDED) }

        val netShieldState = settingsViewModel.viewState.first().netShield
        assertNotNull(netShieldState)
        assertCommonProperties(
            expectedValue = true,
            expectedTitle = R.string.netshield_feature_name,
            expectedSettingValue = SettingValue.SettingStringRes(R.string.netshield_state_on),
            expectedIsRestricted = false,
            settingState = netShieldState
        )
    }

    @Test
    fun `GIVEN business essential user WHEN disabling NetShield THEN NetShield setting is off`() = testScope.runTest {
        testUserProvider.vpnUser = businessEssentialUser

        settingsManager.update { it.copy(netShield = NetShieldProtocol.DISABLED) }

        val netShieldState = settingsViewModel.viewState.first().netShield
        assertNotNull(netShieldState)
        assertCommonProperties(
            expectedValue = false,
            expectedTitle = R.string.netshield_feature_name,
            expectedSettingValue = SettingValue.SettingStringRes(R.string.netshield_state_off),
            expectedIsRestricted = false,
            settingState = netShieldState
        )
    }

    @Test
    fun `netshield conflict shown when custom DNS enabled in profile`() = testScope.runTest {
        val intent = ConnectIntent.FastestInCountry(
            country = CountryId.fastest,
            features = emptySet(),
            profileId = 1L,
            settingsOverrides = createSettingsOverrides(
                netShield = NetShieldProtocol.ENABLED_EXTENDED,
                customDns = CustomDnsSettings(toggleEnabled = true, rawDnsList = listOf("1.1.1.1")),
            )
        )
        val profile = createProfileEntity(1L, name = "Profile 1", connectIntent = intent).toProfile()
        getProfileById.set(profile)
        val connectionParams = createConnectionParams(intent)

        isPrivateDnsActive.value = false
        vpnStateMonitor.updateStatus(VpnStateMonitor.Status(VpnState.Connected, connectionParams))

        val state = settingsViewModel.viewState.first()
        assertIs<SettingValue.SettingOverrideValue>(state.netShield?.settingValueView)
        assertEquals(
            "Profile 1",
            state.netShield?.settingValueView?.connectIntentPrimaryLabel?.name
        )
        assertEquals(R.string.netshield_state_unavailable, state.netShield?.settingValueView?.subtitleRes)
    }

    @Test
    fun `netshield conflict shows no profile override when Private DNS is active`() = testScope.runTest {
        val intent = ConnectIntent.FastestInCountry(
            country = CountryId.fastest,
            features = emptySet(),
            profileId = 1L,
            settingsOverrides = createSettingsOverrides(
                netShield = NetShieldProtocol.ENABLED_EXTENDED,
                customDns = CustomDnsSettings(toggleEnabled = true, rawDnsList = listOf("1.1.1.1")),
            )
        )
        val profile = createProfileEntity(1L, name = "Profile 1", connectIntent = intent).toProfile()
        getProfileById.set(profile)
        val connectionParams = createConnectionParams(intent)

        isPrivateDnsActive.value = true
        vpnStateMonitor.updateStatus(VpnStateMonitor.Status(VpnState.Connected, connectionParams))

        val state = settingsViewModel.viewState.first()
        assertIs<SettingValue.SettingStringRes>(state.netShield?.settingValueView)
        assertEquals(R.string.netshield_state_unavailable, state.netShield?.settingValueView?.subtitleRes)
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
        settingState: SettingViewState<T>
    ) {
        assertEquals(expectedValue, settingState.value)
        assertEquals(expectedTitle, settingState.titleRes)
        assertEquals(expectedSettingValue, settingState.settingValueView)
        assertEquals(expectedIsRestricted, settingState.isRestricted)
    }

    private fun createConnectionParams(intent: ConnectIntent): ConnectionParams {
        val server = createServer()
        return ConnectionParams(
            connectIntent = intent,
            server = server,
            connectingDomain = server.connectingDomains.first(),
            protocol = null,
        )
    }
}
