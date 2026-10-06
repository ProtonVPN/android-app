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

package com.protonvpn.app.telemetry

import com.protonvpn.android.appconfig.AppFeaturesPrefs
import com.protonvpn.android.telemetry.IsWithinFreshInstallPeriod
import com.protonvpn.test.shared.MockSharedPreferencesProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.time.Duration.Companion.hours

private const val INSTALL_TIME = 1_000_000L

class IsWithinFreshInstallPeriodTests {

    private lateinit var appFeaturesPrefs: AppFeaturesPrefs
    private var currentTime = INSTALL_TIME
    private lateinit var isWithinFreshInstallPeriod: IsWithinFreshInstallPeriod

    @Before
    fun setup() {
        appFeaturesPrefs = AppFeaturesPrefs(MockSharedPreferencesProvider())
        isWithinFreshInstallPeriod = IsWithinFreshInstallPeriod(appFeaturesPrefs) { currentTime }
    }

    @Test
    fun `when install timestamp is not recorded then grace period is over`() {
        assertFalse(isWithinFreshInstallPeriod())
    }

    @Test
    fun `when just installed then within grace period`() {
        appFeaturesPrefs.installTimestamp = INSTALL_TIME
        assertTrue(isWithinFreshInstallPeriod())
    }

    @Test
    fun `when just under 24h after install then within grace period`() {
        appFeaturesPrefs.installTimestamp = INSTALL_TIME
        currentTime = INSTALL_TIME + 24.hours.inWholeMilliseconds - 1
        assertTrue(isWithinFreshInstallPeriod())
    }

    @Test
    fun `when 24h have passed since install then grace period is over`() {
        appFeaturesPrefs.installTimestamp = INSTALL_TIME
        currentTime = INSTALL_TIME + 24.hours.inWholeMilliseconds
        assertFalse(isWithinFreshInstallPeriod())
    }
}
