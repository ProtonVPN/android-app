/*
 * Copyright (c) 2023. Proton AG
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

package com.protonvpn.app.settings.data

import com.protonvpn.android.appconfig.FeatureFlags
import com.protonvpn.android.appconfig.GetFeatureFlags
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.netshield.NetShieldProtocol
import com.protonvpn.android.settings.data.EffectiveCurrentUserSettingsFlow
import com.protonvpn.android.settings.data.LocalUserSettings
import com.protonvpn.android.tv.IsTvCheck
import com.protonvpn.test.shared.TestCurrentUserProvider
import com.protonvpn.test.shared.TestUser
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EffectiveCurrentUserSettingsFlowTests {

    @MockK
    private lateinit var mockIsTv: IsTvCheck

    private lateinit var featureFlagsFlow: MutableStateFlow<FeatureFlags>
    private lateinit var rawSettingsFlow: MutableStateFlow<LocalUserSettings>
    private lateinit var testScope: TestScope
    private lateinit var testUserProvider: TestCurrentUserProvider

    private lateinit var effectiveSettingsFlow: EffectiveCurrentUserSettingsFlow

    private val freeUser = TestUser.freeUser.vpnUser
    private val plusUser = TestUser.plusUser.vpnUser

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        testScope = TestScope(UnconfinedTestDispatcher())

        testUserProvider = TestCurrentUserProvider(plusUser)
        featureFlagsFlow = MutableStateFlow(FeatureFlags())
        rawSettingsFlow = MutableStateFlow(LocalUserSettings.Default)

        every { mockIsTv.invoke() } returns false

        val currentUser = CurrentUser(testScope.backgroundScope, testUserProvider)
        effectiveSettingsFlow = EffectiveCurrentUserSettingsFlow(
            rawSettingsFlow,
            GetFeatureFlags(featureFlagsFlow),
            currentUser,
            mockIsTv
        )
    }

    @Test
    fun `LAN connection is always enabled on TV`() = testScope.runTest {
        every { mockIsTv.invoke() } returns true
        rawSettingsFlow.update { it.copy(lanConnections = false) }
        assertTrue(effectiveSettings().lanConnections)
    }

    @Test
    fun `LAN connections matches raw setting on mobile`() = testScope.runTest {
        rawSettingsFlow.update { it.copy(lanConnections = false) }
        assertFalse(effectiveSettings().lanConnections)
        rawSettingsFlow.update { it.copy(lanConnections = true) }
        assertTrue(effectiveSettings().lanConnections)
    }

    @Test
    fun `NetShield only available to paying users`() = testScope.runTest {
        featureFlagsFlow.update { it.copy(netShieldEnabled = true) }
        testUserProvider.vpnUser = freeUser
        rawSettingsFlow.update { it.copy(netShield = NetShieldProtocol.ENABLED) }
        assertEquals(NetShieldProtocol.DISABLED, effectiveSettings().netShield)

        testUserProvider.vpnUser = plusUser
        assertEquals(NetShieldProtocol.ENABLED, effectiveSettings().netShield)
    }

    @Test
    fun `NetShield only available when feature flag enabled`() = testScope.runTest {
        rawSettingsFlow.update { it.copy(netShield = NetShieldProtocol.ENABLED) }
        featureFlagsFlow.update { it.copy(netShieldEnabled = false) }
        assertEquals(NetShieldProtocol.DISABLED, effectiveSettings().netShield)

        featureFlagsFlow.update { it.copy(netShieldEnabled = true) }
        assertEquals(NetShieldProtocol.ENABLED, effectiveSettings().netShield)
    }

    @Test
    fun `SafeMode is defined only when feature flag enabled`() = testScope.runTest {
        featureFlagsFlow.update { it.copy(safeMode = false) }
        rawSettingsFlow.update { it.copy(safeMode = true) }
        assertNull(effectiveSettings().safeMode)

        rawSettingsFlow.update { it.copy(safeMode = false) }
        assertNull(effectiveSettings().safeMode)

        featureFlagsFlow.update { it.copy(safeMode = true) }
        assertEquals(false, effectiveSettings().safeMode)

        rawSettingsFlow.update { it.copy(safeMode = true) }
        assertEquals(true, effectiveSettings().safeMode)
    }

    @Test
    fun `telemetry setting in effect only when feature flag enabled`() = testScope.runTest {
        featureFlagsFlow.update { it.copy(telemetry = false) }
        rawSettingsFlow.update { it.copy(telemetry = true) }
        assertFalse(effectiveSettings().telemetry)

        featureFlagsFlow.update { it.copy(telemetry = true) }
        assertTrue(effectiveSettings().telemetry)

        rawSettingsFlow.update { it.copy(telemetry = false) }
        assertFalse(effectiveSettings().telemetry)
    }

    @Test
    fun `VPN Accelerator can only be disabled when feature is enabled`() = testScope.runTest {
        rawSettingsFlow.update { it.copy(vpnAccelerator = false) }
        featureFlagsFlow.update { it.copy(vpnAccelerator = false) }
        assertTrue(effectiveSettings().vpnAccelerator)

        featureFlagsFlow.update { it.copy(vpnAccelerator = true) }
        assertFalse(effectiveSettings().vpnAccelerator)
    }

    @Test
    fun `VPN Accelerator matches user setting when feature enabled`() = testScope.runTest {
        featureFlagsFlow.update { it.copy(vpnAccelerator = true) }
        rawSettingsFlow.update { it.copy(vpnAccelerator = true) }
        assertTrue(effectiveSettings().vpnAccelerator)

        rawSettingsFlow.update { it.copy(vpnAccelerator = false) }
        assertFalse(effectiveSettings().vpnAccelerator)
    }

    @Test
    fun `VPN Accelerator notifications in effect only when feature and main setting is enabled`() = testScope.runTest {
        rawSettingsFlow.update { it.copy(vpnAccelerator = true, vpnAcceleratorNotifications = true) }
        featureFlagsFlow.update { it.copy(vpnAccelerator = false) }
        assertFalse(effectiveSettings().vpnAcceleratorNotifications)

        rawSettingsFlow.update { it.copy(vpnAccelerator = false, vpnAcceleratorNotifications = true) }
        featureFlagsFlow.update { it.copy(vpnAccelerator = true) }
        assertFalse(effectiveSettings().vpnAcceleratorNotifications)

        rawSettingsFlow.update { it.copy(vpnAccelerator = true, vpnAcceleratorNotifications = true) }
        assertTrue(effectiveSettings().vpnAcceleratorNotifications)

        rawSettingsFlow.update { it.copy(vpnAccelerator = true, vpnAcceleratorNotifications = false) }
        assertFalse(effectiveSettings().vpnAcceleratorNotifications)
    }

    private suspend fun effectiveSettings() = effectiveSettingsFlow.first()
}
