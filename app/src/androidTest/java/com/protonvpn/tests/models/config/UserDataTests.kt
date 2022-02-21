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

package com.protonvpn.tests.models.config

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.protonvpn.android.appconfig.FeatureFlags
import com.protonvpn.android.models.config.UserData
import com.protonvpn.android.utils.Storage
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class UserDataTests {

    @get:Rule
    val rule = InstantTaskExecutorRule()

    private lateinit var userData: UserData

    @Before
    fun setup() {
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
    fun testIsSafeModeFeatureDisabled() {
        val featureFlags = FeatureFlags(safeMode = false)

        userData.safeModeEnabled = null
        assertFalse(userData.isSafeModeEnabled(featureFlags))
        userData.safeModeEnabled = true
        assertTrue(userData.isSafeModeEnabled(featureFlags))
        userData.safeModeEnabled = false
        assertFalse(userData.isSafeModeEnabled(featureFlags))
    }

    @Test
    fun testIsSafeModeFeatureEnabled() {
        val featureFlags = FeatureFlags(safeMode = true)

        userData.safeModeEnabled = null
        assertTrue(userData.isSafeModeEnabled(featureFlags))
        userData.safeModeEnabled = true
        assertTrue(userData.isSafeModeEnabled(featureFlags))
        userData.safeModeEnabled = false
        assertFalse(userData.isSafeModeEnabled(featureFlags))
    }
}
