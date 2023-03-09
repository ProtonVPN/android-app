/*
 *  Copyright (c) 2022 Proton AG
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

package com.protonvpn.tests.signup

import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.filters.SdkSuppress
import com.protonvpn.actions.OnboardingRobot
import com.protonvpn.android.appconfig.AppConfig
import com.protonvpn.android.ui.main.MobileMainActivity
import com.protonvpn.mocks.TestApiConfig
import com.protonvpn.testRules.ProtonHiltAndroidRule
import com.protonvpn.testRules.ProtonHiltInjectRule
import com.protonvpn.testsHelper.TestSetup
import dagger.hilt.android.testing.HiltAndroidTest
import me.proton.core.auth.test.MinimalSignUpExternalTests
import me.proton.core.auth.test.rule.AcceptExternalRule
import me.proton.core.network.domain.client.ExtraHeaderProvider
import org.junit.Before
import org.junit.Rule
import org.junit.runner.RunWith
import javax.inject.Inject

@LargeTest
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 28) // Signups tests does not work on older versions due to animations bug
@HiltAndroidTest
class SignupTests : MinimalSignUpExternalTests {

    @get:Rule(order = 0)
    val hiltRule = ProtonHiltAndroidRule(this, TestApiConfig.Backend)

    @get:Rule(order = 1)
    val injectRule = ProtonHiltInjectRule(hiltRule)

    @get:Rule(order = 2)
    val acceptExternalRule = AcceptExternalRule { extraHeaderProvider }

    @get:Rule(order = 3)
    val activityRule = ActivityScenarioRule(MobileMainActivity::class.java)

    @Inject
    lateinit var appConfig: AppConfig

    @Inject
    lateinit var extraHeaderProvider: ExtraHeaderProvider

    @Before
    fun setUp() {
        TestSetup.setCompletedOnboarding()
        TestSetup.quark?.jailUnban()
    }

    override val isCongratsDisplayed = false

    override fun verifyAfter() {
        OnboardingRobot()
            .apply { verify { welcomeScreenIsDisplayed() } }
            .completeOnboarding(appConfig.getFeatureFlags().telemetry)
            .skipOnboarding() // We have no way to connect in tests at the moment.
            .verify { isInMainScreen() }
    }
}
