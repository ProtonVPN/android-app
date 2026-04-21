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

package com.protonvpn.tests.telemetry

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.protonvpn.android.telemetry.settings.GetSettingsTelemetryHeartbeatDimensions
import com.protonvpn.mocks.TestApiConfig
import com.protonvpn.testRules.ProtonHiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

@RunWith(AndroidJUnit4::class)
@HiltAndroidTest
class GetSettingsTelemetryHeartbeatDimensionsTests {

    @get:Rule
    val protonRule = ProtonHiltAndroidRule(this, TestApiConfig.Mocked())

    @Inject lateinit var getDimensions: GetSettingsTelemetryHeartbeatDimensions

    @Before
    fun setup() {
        protonRule.inject()
    }

    @Test
    fun settingsTelemetryDoesNotBlockWhenNoUserLoggedIn() = runTest(timeout = 10.seconds) {
        getDimensions()
    }
}