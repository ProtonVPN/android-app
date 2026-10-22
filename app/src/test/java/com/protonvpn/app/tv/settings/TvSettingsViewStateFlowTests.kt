/*
 * Copyright (c) 2026. Proton AG
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

package com.protonvpn.app.tv.settings

import com.protonvpn.android.R
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.managed.AutoLoginConfig
import com.protonvpn.android.managed.ManagedConfig
import com.protonvpn.android.settings.data.CustomDnsSettings
import com.protonvpn.android.settings.data.EffectiveCurrentUserSettings
import com.protonvpn.android.settings.data.LocalUserSettings
import com.protonvpn.android.settings.data.SplitTunnelingSettings
import com.protonvpn.android.tv.settings.FakeIsTvAutoConnectFeatureFlagEnabled
import com.protonvpn.android.tv.settings.main.ui.TvSetting
import com.protonvpn.android.tv.settings.main.ui.TvSettingAction
import com.protonvpn.android.tv.settings.main.ui.TvSettingValue
import com.protonvpn.android.tv.settings.main.ui.TvSettingsViewState
import com.protonvpn.android.tv.settings.main.ui.TvSettingsViewStateFlow
import com.protonvpn.android.ui.settings.BuildConfigInfo
import com.protonvpn.android.vpn.IsPrivateDnsActiveFlow
import com.protonvpn.android.vpn.ProtocolSelection
import com.protonvpn.test.shared.TestCurrentUserProvider
import com.protonvpn.test.shared.TestUser
import com.protonvpn.test.shared.createAccountUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import me.proton.core.user.domain.entity.Type
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import kotlin.test.assertIs

class TvSettingsViewStateFlowTests {

    private lateinit var testScope: TestScope
    private lateinit var testUserProvider: TestCurrentUserProvider
    private lateinit var settingsFlow: MutableStateFlow<LocalUserSettings>
    private lateinit var isPrivateDnsActive: MutableStateFlow<Boolean>
    private lateinit var autoLoginConfig: MutableStateFlow<AutoLoginConfig?>

    private val allEnabledSettings = LocalUserSettings.Default.copy(
        isNetShieldEnabled = true,
        splitTunneling = SplitTunnelingSettings(isEnabled = true),
        protocol = ProtocolSelection.SMART,
        tvAutoConnectOnBoot = true,
        lanConnections = true,
        customDns = CustomDnsSettings(toggleEnabled = true, rawDnsList = listOf("1.1.1.1")),
        ipV6Enabled = true,
    )

    @Before
    fun setup() {
        testScope = TestScope(UnconfinedTestDispatcher())
        testUserProvider = TestCurrentUserProvider(
            TestUser.plusUser.vpnUser,
            createAccountUser().copy(email = "user@proton.me")
        )
        settingsFlow = MutableStateFlow(allEnabledSettings)
        isPrivateDnsActive = MutableStateFlow(false)
        autoLoginConfig = MutableStateFlow(null)
    }

    private fun createFlow(isAutoConnectFlagEnabled: Boolean = true) = TvSettingsViewStateFlow(
        currentUser = CurrentUser(testUserProvider),
        effectiveSettings = EffectiveCurrentUserSettings(testScope.backgroundScope, settingsFlow),
        isPrivateDnsActiveFlow = IsPrivateDnsActiveFlow(isPrivateDnsActive),
        isTvAutoConnectFeatureFlagEnabled = FakeIsTvAutoConnectFeatureFlagEnabled(isAutoConnectFlagEnabled),
        managedConfig = ManagedConfig(autoLoginConfig),
        buildConfigInfo = BuildConfigInfo(
            apiUrl = "https://api.proton.me".toHttpUrl(),
            dohProviders = emptyArray(),
            certificatePins = emptyArray(),
            alternativeApiPins = emptyList(),
            humanVerificationApiHost = "verify.proton.me",
        ),
    )

    private suspend fun TvSettingsViewStateFlow.firstState(): TvSettingsViewState = invoke().first()

    private fun TvSettingsViewState.values(): Map<TvSetting, TvSettingValue> =
        settings.associate { it.setting to it.value }

    @Test
    fun `plus user sees setting values and can open settings`() = testScope.runTest {
        val state = createFlow().firstState()
        val expected = listOf(
            TvSetting.NetShield to TvSettingValue.Text(R.string.netshield_state_unavailable),
            TvSetting.SplitTunneling to TvSettingValue.Text(R.string.split_tunneling_state_on),
            TvSetting.Protocol to TvSettingValue.Text(R.string.settingsProtocolNameSmart),
            TvSetting.AutoConnect to TvSettingValue.Text(R.string.auto_connect_state_on),
            TvSetting.Lan to TvSettingValue.Text(R.string.lan_state_on),
            TvSetting.CustomDns to TvSettingValue.Text(R.string.custom_dns_state_on),
            TvSetting.IPv6 to TvSettingValue.Text(R.string.ipv6_state_on),
            TvSetting.ReportIssue to TvSettingValue.None,
            TvSetting.SignOut to TvSettingValue.None,
        )
        assertEquals(expected, state.settings.map { it.setting to it.value })
        state.settings
            .filterNot { it.setting == TvSetting.SignOut }
            .forEach {
                assertIs<TvSettingAction.OpenActivity>(
                    it.action,
                    "Unexpected action for ${it.setting}: ${it.action}"
                )
            }
    }

    @Test
    fun `plus user sees settings that are off`() = testScope.runTest {
        settingsFlow.value = allEnabledSettings.copy(
            isNetShieldEnabled = false,
            splitTunneling = SplitTunnelingSettings(isEnabled = false),
            tvAutoConnectOnBoot = false,
            lanConnections = false,
            customDns = CustomDnsSettings(toggleEnabled = false),
            ipV6Enabled = false,
        )
        val values = createFlow().firstState().values()
        assertEquals(TvSettingValue.Text(R.string.netshield_state_off), values[TvSetting.NetShield])
        assertEquals(TvSettingValue.Text(R.string.split_tunneling_state_off), values[TvSetting.SplitTunneling])
        assertEquals(TvSettingValue.Text(R.string.auto_connect_state_off), values[TvSetting.AutoConnect])
        assertEquals(TvSettingValue.Text(R.string.lan_state_off), values[TvSetting.Lan])
        assertEquals(TvSettingValue.Text(R.string.custom_dns_state_off), values[TvSetting.CustomDns])
        assertEquals(TvSettingValue.Text(R.string.ipv6_state_off), values[TvSetting.IPv6])
    }

    @Test
    fun `free user has paid settings restricted`() = testScope.runTest {
        testUserProvider.vpnUser = TestUser.freeUser.vpnUser
        val values = createFlow().firstState().values()
        assertEquals(TvSettingValue.Restricted, values[TvSetting.NetShield])
        assertEquals(TvSettingValue.Restricted, values[TvSetting.SplitTunneling])
        assertEquals(TvSettingValue.Restricted, values[TvSetting.Lan])
        assertEquals(TvSettingValue.Restricted, values[TvSetting.CustomDns])
        assertEquals(TvSettingValue.Text(R.string.settingsProtocolNameSmart), values[TvSetting.Protocol])
        assertEquals(TvSettingValue.Text(R.string.auto_connect_state_on), values[TvSetting.AutoConnect])
        assertEquals(TvSettingValue.Text(R.string.ipv6_state_on), values[TvSetting.IPv6])
    }

    @Test
    fun `when private DNS is active then NetShield and custom DNS are unavailable`() = testScope.runTest {
        settingsFlow.value = allEnabledSettings.copy(customDns = CustomDnsSettings(toggleEnabled = false))
        isPrivateDnsActive.value = true
        val values = createFlow().firstState().values()
        assertEquals(TvSettingValue.Text(R.string.netshield_state_unavailable), values[TvSetting.NetShield])
        assertEquals(TvSettingValue.Text(R.string.custom_dns_state_unavailable), values[TvSetting.CustomDns])
    }

    @Test
    fun `when auto-connect feature flag is disabled then auto-connect is not shown`() = testScope.runTest {
        val state = createFlow(isAutoConnectFlagEnabled = false).firstState()
        assertEquals(null, state.values()[TvSetting.AutoConnect])
    }

    @Test
    fun `on managed device sign out is not shown`() = testScope.runTest {
        autoLoginConfig.value = AutoLoginConfig.UsernamePassword("user", "password")
        val state = createFlow().firstState()
        assertEquals(null, state.values()[TvSetting.SignOut])
    }

    @Test
    fun `credential-less user can sign out`() = testScope.runTest {
        testUserProvider.user = createAccountUser(type = Type.CredentialLess)
        val state = createFlow().firstState()
        assertEquals(TvSettingValue.None, state.values()[TvSetting.SignOut])
    }

    @Test
    fun `caption shows email and plan name`() = testScope.runTest {
        val state = createFlow().firstState()
        assertEquals("user@proton.me", state.accountEmail)
        assertEquals("vpnplus", state.planName)
    }
}
