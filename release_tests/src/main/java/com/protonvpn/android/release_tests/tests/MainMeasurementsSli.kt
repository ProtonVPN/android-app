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
import com.protonvpn.android.release_tests.data.TestConstants
import com.protonvpn.android.release_tests.helpers.TestApiClient
import com.protonvpn.android.release_tests.robots.CountriesRobot
import com.protonvpn.android.release_tests.robots.HomeRobot
import com.protonvpn.android.release_tests.robots.LoginRobot
import com.protonvpn.android.release_tests.rules.LaunchVpnAppRule
import me.proton.core.test.performance.MeasurementProfile
import me.proton.core.test.performance.MeasurementRule
import me.proton.core.test.performance.annotation.Measure
import me.proton.core.test.performance.measurement.DurationMeasurement
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

@RequiresApi(Build.VERSION_CODES.O)
class MainMeasurementsSli {

    private lateinit var profile: MeasurementProfile
    private val measurementRule = MeasurementRule()
    private val measurementContext = measurementRule.measurementContext(LokiConfig.measurementConfig)

    @get:Rule
    val rule: RuleChain = RuleChain
        .outerRule(LaunchVpnAppRule())
        .around(measurementRule)

    @Before
    fun setup() {
        LoginRobot.navigateToSignIn()
            .enterCredentials("testas3", BuildConfig.TEST_ACCOUNT_PASSWORD)
            .pressSignIn()
    }

    @Test
    @Measure
    fun loginSli() {
        profile = measurementContext
            .setWorkflow("login_flow")
            .setServiceLevelIndicator("login")
            .addMeasurement(DurationMeasurement())
            .setLogcatFilter(LokiConfig.logcatFilter)

        profile.measure {
            LoginRobot.waitUntilLoggedIn()
        }
    }

    @Test
    @Measure
    fun connectionSli(){
        profile = measurementContext
            .setWorkflow("connection_flow")
            .setServiceLevelIndicator("quick_connect")
            .addMeasurement(DurationMeasurement())
            .setLogcatFilter(LokiConfig.logcatFilter)

        LoginRobot.waitUntilLoggedIn()
        HomeRobot.connect()

        profile.measure {
            HomeRobot.waitForNotificationPermissionRequest()
        }
        disconnectWithDelay()
    }

    @Test
    @Measure
    fun connectionToSpecificServer(){
       profile = measurementContext
            .setWorkflow("specific_server_connection_flow")
            .setServiceLevelIndicator("specific_server_connect")
            .setLogcatFilter(LokiConfig.logcatFilter)

        val server: String = TestApiClient.getRandomServer()

        LoginRobot.waitUntilLoggedIn()
        HomeRobot.navigateToCountries()
        CountriesRobot.clickOnSearchIcon()
            .searchFor(server)
            .connectTo(server)
        HomeRobot.allowVpnPermission()
        profile.measure {
            HomeRobot.waitForNotificationPermissionRequest()
        }
        disconnectWithDelay()
    }

    @After
    fun tearDown(){
        profile.pushLogcatLogs()
        profile.clearLogcatLogs()
    }

    private fun disconnectWithDelay(){
        HomeRobot.dismissNotificationRequest()
            .disconnect()
        //Delay to prevent issues when sending SLI results
        Thread.sleep(TestConstants.FIVE_SECONDS_TIMEOUT_MS)
    }
}