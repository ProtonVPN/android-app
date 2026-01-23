/*
 *
 *  * Copyright (c) 2023. Proton AG
 *  *
 *  * This file is part of ProtonVPN.
 *  *
 *  * ProtonVPN is free software: you can redistribute it and/or modify
 *  * it under the terms of the GNU General Public License as published by
 *  * the Free Software Foundation, either version 3 of the License, or
 *  * (at your option) any later version.
 *  *
 *  * ProtonVPN is distributed in the hope that it will be useful,
 *  * but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  * GNU General Public License for more details.
 *  *
 *  * You should have received a copy of the GNU General Public License
 *  * along with ProtonVPN.  If not, see <https://www.gnu.org/licenses/>.
 *
 */

package com.protonvpn.android.release_tests.tests

import android.os.Build
import androidx.annotation.RequiresApi
import com.protonvpn.android.release_tests.BuildConfig
import com.protonvpn.android.release_tests.data.LokiConfig
import com.protonvpn.android.release_tests.helpers.BtiScenarios
import com.protonvpn.android.release_tests.helpers.TestApiClient
import com.protonvpn.android.release_tests.rules.BtiScenarioRule
import com.protonvpn.android.release_tests.rules.LaunchVpnAppRule
import com.protonvpn.android.release_tests.rules.ScreenshotTakingRule
import com.protonvpn.android.ui_automator_test_util.data.TestConstants
import com.protonvpn.android.ui_automator_test_util.robots.LoginRobot
import me.proton.core.test.performance.MeasurementProfile
import me.proton.core.test.performance.MeasurementRule
import me.proton.core.test.performance.annotation.Measure
import me.proton.core.test.performance.measurement.DurationMeasurement
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

@RequiresApi(Build.VERSION_CODES.O)
class AltRoutingSli {

    private lateinit var profile: MeasurementProfile
    private val measurementRule = MeasurementRule()
    private val measurementContext = measurementRule.measurementContext(LokiConfig.measurementConfig)

    private val altRoutingScenario = listOf(BtiScenarios.BLOCK_PROD_ENDPOINT)

    @get:Rule
    val rule: RuleChain = RuleChain
        .outerRule(BtiScenarioRule(altRoutingScenario))
        .around(LaunchVpnAppRule())
        .around(ScreenshotTakingRule())
        .around(measurementRule)

    @Test
    @Measure
    fun loginSli() {
        profile = measurementContext
            .setWorkflow("alternative_routing")
            .setServiceLevelIndicator("alt_routing_login")
            .addMeasurement(DurationMeasurement())
            .setLogcatFilter(LokiConfig.logcatFilter)

        LoginRobot.signIn(TestConstants.USERNAME, BuildConfig.TEST_ACCOUNT_PASSWORD)

        profile.measure {
            LoginRobot.waitUntilLoggedIn()
        }
    }

    @After
    fun tearDown(){
        TestApiClient.setBtiScenario(BtiScenarios.RESET)
        profile.pushLogcatLogs()
        profile.clearLogcatLogs()
    }
}
