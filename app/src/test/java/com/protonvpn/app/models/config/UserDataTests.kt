/*
 * Copyright (c) 2022. Proton AG
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

package com.protonvpn.app.models.config

import android.content.SharedPreferences
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.protonvpn.android.appconfig.FeatureFlags
import com.protonvpn.android.models.config.UserData
import com.protonvpn.android.utils.Storage
import com.protonvpn.test.shared.MockSharedPreference
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class UserDataTests {

    @get:Rule
    val rule = InstantTaskExecutorRule()

    private lateinit var userData: UserData
    private lateinit var fakePrefernces: SharedPreferences

    @Before
    fun setup() {
        fakePrefernces = MockSharedPreference()
        Storage.setPreferences(fakePrefernces)
        userData = UserData.create()
    }

    @After
    fun teardown() {
        Storage.clearAllPreferencesSync()
    }

    @Test
    fun testIsVpnAcceleratorFeatureDisabled() {
        val featureFlags = FeatureFlags(vpnAccelerator = false)

        userData.vpnAcceleratorEnabled = false
        assertTrue(userData.isVpnAcceleratorEnabled(featureFlags))
        userData.vpnAcceleratorEnabled = true
        assertTrue(userData.isVpnAcceleratorEnabled(featureFlags))
    }

    @Test
    fun testIsVpnAcceleratorFeatureEnabled() {
        val featureFlags = FeatureFlags(vpnAccelerator = true)

        userData.vpnAcceleratorEnabled = false
        assertFalse(userData.isVpnAcceleratorEnabled(featureFlags))
        userData.vpnAcceleratorEnabled = true
        assertTrue(userData.isVpnAcceleratorEnabled(featureFlags))
    }

    @Test
    fun safeModeUndefined_when_featureDisabled() {
        testSafeMode(expected = null, featureEnabled = false, setting = false)
    }

    @Test
    fun safeModeSameAsSetting_when_featureEnabled() {
        testSafeMode(expected = false, featureEnabled = true, setting = false)
        testSafeMode(expected = true, featureEnabled = true, setting = true)
    }

    private fun testSafeMode(expected: Boolean?, featureEnabled: Boolean, setting: Boolean) {
        userData.safeModeEnabled = setting
        val effectiveSafeMode = userData.isSafeModeEnabled(FeatureFlags(safeMode = featureEnabled))
        assertEquals(expected, effectiveSafeMode)
    }
}
