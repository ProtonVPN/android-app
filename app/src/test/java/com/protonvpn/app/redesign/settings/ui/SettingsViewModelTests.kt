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

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import com.protonvpn.android.R
import com.protonvpn.android.appconfig.ChangeServerConfig
import com.protonvpn.android.appconfig.FeatureFlags
import com.protonvpn.android.appconfig.GetFeatureFlags
import com.protonvpn.android.appconfig.Restrictions
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.components.InstalledAppsProvider
import com.protonvpn.android.models.config.TransmissionProtocol
import com.protonvpn.android.models.config.VpnProtocol
import com.protonvpn.android.netshield.NetShieldProtocol
import com.protonvpn.android.redesign.settings.ui.SettingsViewModel
import com.protonvpn.android.settings.data.CurrentUserLocalSettingsManager
import com.protonvpn.android.settings.data.EffectiveCurrentUserSettings
import com.protonvpn.android.settings.data.EffectiveCurrentUserSettingsFlow
import com.protonvpn.android.settings.data.LocalUserSettings
import com.protonvpn.android.settings.data.LocalUserSettingsStoreProvider
import com.protonvpn.android.tv.IsTvCheck
import com.protonvpn.android.ui.settings.BuildConfigInfo
import com.protonvpn.android.userstorage.DontShowAgainStateStoreProvider
import com.protonvpn.android.userstorage.DontShowAgainStore
import com.protonvpn.android.vpn.ProtocolSelection
import com.protonvpn.android.vpn.VpnConnectionManager
import com.protonvpn.android.vpn.VpnState
import com.protonvpn.android.vpn.VpnStateMonitor
import com.protonvpn.android.vpn.VpnStatusProviderUI
import com.protonvpn.android.vpn.VpnUiDelegate
import com.protonvpn.test.shared.InMemoryDataStoreFactory
import com.protonvpn.test.shared.TestCurrentUserProvider
import com.protonvpn.test.shared.TestUser
import com.protonvpn.test.shared.createAccountUser
import io.mockk.MockKAnnotations
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTests {

    @RelaxedMockK
    private lateinit var mockBuildConfigInfo: BuildConfigInfo

    @MockK
    private lateinit var mockIsTvCheck: IsTvCheck

    @RelaxedMockK
    private lateinit var mockInstalledAppsProvider: InstalledAppsProvider

    @RelaxedMockK
    private lateinit var mockConnectionManager: VpnConnectionManager

    @RelaxedMockK
    private lateinit var mockUiDelegate: VpnUiDelegate

    private lateinit var effectiveSettings: EffectiveCurrentUserSettings
    private lateinit var settingsManager: CurrentUserLocalSettingsManager
    private lateinit var rawSettingsFlow: MutableStateFlow<LocalUserSettings>
    private lateinit var testScope: TestScope
    private lateinit var testUserProvider: TestCurrentUserProvider
    private lateinit var vpnStateMonitor: VpnStateMonitor
    private lateinit var dontShowAgainStore: DontShowAgainStore

    private lateinit var settingsViewModel: SettingsViewModel

    private val businessEssentialUser = TestUser.businessEssential.vpnUser
    private val freeUser = TestUser.freeUser.vpnUser
    private val plusUser = TestUser.plusUser.vpnUser

    @Before
    fun setup() {
        MockKAnnotations.init(this)

        val testDispatcher = UnconfinedTestDispatcher()
        Dispatchers.setMain(testDispatcher)
        testScope = TestScope(testDispatcher)
        every { mockIsTvCheck.invoke() } returns false

        val accountUser = createAccountUser()
        testUserProvider = TestCurrentUserProvider(plusUser, accountUser)
        val currentUser = CurrentUser(testScope.backgroundScope, testUserProvider)
        val changeServerConfig = ChangeServerConfig(30, 3, 60)
        val restrictionsFlow = currentUser.vpnUserFlow.mapLatest { vpnUser ->
            Restrictions(
                restrict = vpnUser?.isFreeUser == true,
                changeServerConfig
            )
        }
        val featureFlags = FeatureFlags().copy(
            netShieldEnabled = true,
            vpnAccelerator = true
        )
        val getFeatureFlags = GetFeatureFlags(MutableStateFlow(featureFlags))

        rawSettingsFlow = MutableStateFlow(LocalUserSettings.Default)

        val effectiveCurrentUserSettingsFlow = EffectiveCurrentUserSettingsFlow(
            rawSettingsFlow, getFeatureFlags, currentUser, mockIsTvCheck, restrictionsFlow
        )
        effectiveSettings = EffectiveCurrentUserSettings(
            testScope.backgroundScope, effectiveCurrentUserSettingsFlow
        )
        vpnStateMonitor = VpnStateMonitor()
        dontShowAgainStore = DontShowAgainStore(currentUser, DontShowAgainStateStoreProvider(InMemoryDataStoreFactory()))
        settingsManager = CurrentUserLocalSettingsManager(
            LocalUserSettingsStoreProvider(InMemoryDataStoreFactory()),
        )

        settingsViewModel = SettingsViewModel(
            SavedStateHandle(),
            currentUser,
            settingsManager,
            effectiveSettings,
            mockBuildConfigInfo,
            mockInstalledAppsProvider,
            mockConnectionManager,
            VpnStatusProviderUI(testScope.backgroundScope, vpnStateMonitor),
            dontShowAgainStore,
        )
    }

    @Test
    fun `netshield enabled for plus users`() = testScope.runTest {
        rawSettingsFlow.update { it.copy(netShield = NetShieldProtocol.ENABLED_EXTENDED) }
        val state = settingsViewModel.viewState.first()
        assertCommonProperties(
            true, R.string.netshield_feature_name, R.string.netshield_state_on, false, null,
            state.netShield
        )
    }

    @Test
    fun `netshield disabled for plus users`() = testScope.runTest {
        rawSettingsFlow.update { it.copy(netShield = NetShieldProtocol.DISABLED) }
        val state = settingsViewModel.viewState.first()
        assertCommonProperties(
            false, R.string.netshield_feature_name, R.string.netshield_state_off, false, null,
            state.netShield
        )
    }

    @Test
    fun `netshield disabled for free users even if setting is on`() = testScope.runTest {
        rawSettingsFlow.update { it.copy(netShield = NetShieldProtocol.ENABLED_EXTENDED) }
        testUserProvider.vpnUser = freeUser
        val state = settingsViewModel.viewState.first()
        assertCommonProperties(
            false, R.string.netshield_feature_name, R.string.netshield_state_off, true, R.drawable.vpn_plus_badge,
            state.netShield
        )
    }

    @Test
    fun `netshield disabled for B2B-essentials users even if setting is on`() = testScope.runTest {
        rawSettingsFlow.update { it.copy(netShield = NetShieldProtocol.ENABLED_EXTENDED) }
        testUserProvider.vpnUser = businessEssentialUser
        val state = settingsViewModel.viewState.first()
        assertCommonProperties(
            false, R.string.netshield_feature_name, R.string.netshield_state_off, true, null,
            state.netShield
        )
    }

    @Test
    fun `vpn accelerator off for free users`() = testScope.runTest {
        rawSettingsFlow.update { it.copy(vpnAccelerator = false) }
        testUserProvider.vpnUser = freeUser
        val state = settingsViewModel.viewState.first()
        assertCommonProperties(
            false, R.string.settings_vpn_accelerator_title, R.string.vpn_accelerator_state_off, true, R.drawable.vpn_plus_badge,
            state.vpnAccelerator
        )
    }

    @Test
    fun `vpn accelerator off for free users even when enabled`() = testScope.runTest {
        rawSettingsFlow.update { it.copy(vpnAccelerator = true) }
        testUserProvider.vpnUser = freeUser
        val state = settingsViewModel.viewState.first()
        assertCommonProperties(
            false, R.string.settings_vpn_accelerator_title, R.string.vpn_accelerator_state_off, true, R.drawable.vpn_plus_badge,
            state.vpnAccelerator
        )
    }

    @Test
    fun `vpn accelerator enabled for plus users`() = testScope.runTest {
        rawSettingsFlow.update { it.copy(vpnAccelerator = true) }
        testUserProvider.vpnUser = plusUser
        val state = settingsViewModel.viewState.first()
        assertCommonProperties(
            true, R.string.settings_vpn_accelerator_title, R.string.vpn_accelerator_state_on, false, null,
            state.vpnAccelerator
        )
    }

    @Test
    fun `vpn accelerator disabled for plus users`() = testScope.runTest {
        rawSettingsFlow.update { it.copy(vpnAccelerator = false) }
        testUserProvider.vpnUser = plusUser
        val state = settingsViewModel.viewState.first()
        assertCommonProperties(
            false, R.string.settings_vpn_accelerator_title, R.string.vpn_accelerator_state_off, false, null,
            state.vpnAccelerator
        )
    }

    @Test
    fun `lan connection triggers reconnect dialog`() = testScope.runTest {
        vpnStateMonitor.updateStatus(VpnStateMonitor.Status(VpnState.Connected, mockk(relaxed = true)))
        settingsViewModel.toggleLanConnections(mockUiDelegate)
        assertEquals(DontShowAgainStore.Type.LanConnectionsChangeWhenConnected, settingsViewModel.showReconnectDialogFlow.first())
    }

    @Test
    fun `protocol update triggers reconnect dialog`() = testScope.runTest {
        vpnStateMonitor.updateStatus(VpnStateMonitor.Status(VpnState.Connected, mockk(relaxed = true)))
        settingsViewModel.updateProtocol(mockUiDelegate, ProtocolSelection(VpnProtocol.WireGuard, TransmissionProtocol.TLS))
        assertEquals(DontShowAgainStore.Type.ProtocolChangeWhenConnected, settingsViewModel.showReconnectDialogFlow.first())
    }

    @Test
    fun `split tunnel update triggers reconnect dialog`() = testScope.runTest {
        vpnStateMonitor.updateStatus(VpnStateMonitor.Status(VpnState.Connected, mockk(relaxed = true)))
        settingsViewModel.onSplitTunnelingUpdated(mockUiDelegate)
        assertEquals(DontShowAgainStore.Type.SplitTunnelingChangeWhenConnected, settingsViewModel.showReconnectDialogFlow.first())
    }

    @Test
    fun `split tunnel toggle with exclusions triggers reconnect dialog`() = testScope.runTest {
        vpnStateMonitor.updateStatus(VpnStateMonitor.Status(VpnState.Connected, mockk(relaxed = true)))
        settingsManager.update { current ->
            current.copy(splitTunneling = current.splitTunneling.copy(isEnabled = false, excludedApps = listOf("app1")))
        }
        settingsViewModel.onSplitTunnelingUpdated(mockUiDelegate)
        assertEquals(DontShowAgainStore.Type.SplitTunnelingChangeWhenConnected, settingsViewModel.showReconnectDialogFlow.first())
    }

    @Test
    fun `split tunnel toggle with empty exclusions don't triggers reconnect dialog`() = testScope.runTest {
        vpnStateMonitor.updateStatus(VpnStateMonitor.Status(VpnState.Connected, mockk(relaxed = true)))
        settingsViewModel.toggleSplitTunneling(mockUiDelegate)
        assertEquals(null, settingsViewModel.showReconnectDialogFlow.first())
    }

    @Test
    fun `no reconnection dialog when not connected`() = testScope.runTest {
        settingsViewModel.toggleLanConnections(mockUiDelegate)
        assertEquals(null, settingsViewModel.showReconnectDialogFlow.first())
    }

    @Test
    fun `saved reconnection 'yes' choice reconnects automatically`() = testScope.runTest {
        dontShowAgainStore.setChoice(DontShowAgainStore.Type.LanConnectionsChangeWhenConnected, DontShowAgainStore.Choice.Positive)
        vpnStateMonitor.updateStatus(VpnStateMonitor.Status(VpnState.Connected, mockk(relaxed = true)))
        settingsViewModel.toggleLanConnections(mockUiDelegate)
        assertEquals(null, settingsViewModel.showReconnectDialogFlow.first())
        coVerify(exactly = 1) { mockConnectionManager.reconnect(any(), any()) }
    }

    @Test
    fun `saved reconnection 'no' don't reconnects automatically`() = testScope.runTest {
        dontShowAgainStore.setChoice(DontShowAgainStore.Type.LanConnectionsChangeWhenConnected, DontShowAgainStore.Choice.Negative)
        vpnStateMonitor.updateStatus(VpnStateMonitor.Status(VpnState.Connected, mockk(relaxed = true)))
        settingsViewModel.toggleLanConnections(mockUiDelegate)
        assertEquals(null, settingsViewModel.showReconnectDialogFlow.first())
        coVerify(exactly = 0) { mockConnectionManager.reconnect(any(), any()) }
    }

    @Test
    fun `reconnect & save reconnection dialog`() = testScope.runTest {
        vpnStateMonitor.updateStatus(VpnStateMonitor.Status(VpnState.Connected, mockk(relaxed = true)))
        settingsViewModel.toggleLanConnections(mockUiDelegate)
        assertEquals(DontShowAgainStore.Type.LanConnectionsChangeWhenConnected, settingsViewModel.showReconnectDialogFlow.first())
        settingsViewModel.onReconnectClicked(mockUiDelegate, true, DontShowAgainStore.Type.LanConnectionsChangeWhenConnected)
        assertEquals(DontShowAgainStore.Choice.Positive, dontShowAgainStore.getChoice(DontShowAgainStore.Type.LanConnectionsChangeWhenConnected))
        coVerify(exactly = 1) { mockConnectionManager.reconnect(any(), any()) }
    }

    @Test
    fun `dismiss & save reconnection dialog`() = testScope.runTest {
        vpnStateMonitor.updateStatus(VpnStateMonitor.Status(VpnState.Connected, mockk(relaxed = true)))
        settingsViewModel.toggleLanConnections(mockUiDelegate)
        assertEquals(DontShowAgainStore.Type.LanConnectionsChangeWhenConnected, settingsViewModel.showReconnectDialogFlow.first())
        settingsViewModel.dismissReconnectDialog(true, DontShowAgainStore.Type.LanConnectionsChangeWhenConnected)
        assertEquals(DontShowAgainStore.Choice.Negative, dontShowAgainStore.getChoice(DontShowAgainStore.Type.LanConnectionsChangeWhenConnected))
        coVerify(exactly = 0) { mockConnectionManager.reconnect(any(), any()) }
    }

    @Suppress("LongParameterList")
    private fun <T> assertCommonProperties(
        expectedValue: T,
        @StringRes expectedTitle: Int,
        @StringRes expectedSubtitle: Int,
        expectedIsRestricted: Boolean,
        @DrawableRes expectedUpgradeIcon: Int?,
        settingState: SettingsViewModel.SettingViewState<T>
    ) {
        assertEquals(expectedValue, settingState.value)
        assertEquals(expectedTitle, settingState.titleRes)
        assertEquals(expectedSubtitle, settingState.subtitleRes)
        assertEquals(expectedIsRestricted, settingState.isRestricted)
        assertEquals(expectedUpgradeIcon, settingState.upgradeIconRes)
    }
}
