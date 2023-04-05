/*
 *  Copyright (c) 2023 Proton AG
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

package com.protonvpn.tests.login

import androidx.test.espresso.Espresso
import androidx.test.ext.junit.rules.ActivityScenarioRule
import com.protonvpn.android.ui.main.MobileMainActivity
import com.protonvpn.mocks.TestApiConfig
import com.protonvpn.test.shared.TestUser
import com.protonvpn.testRules.ProtonHiltAndroidRule
import com.protonvpn.testsHelper.TestSetup
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import me.proton.core.test.android.robots.auth.AddAccountRobot
import me.proton.core.test.android.robots.auth.login.TwoFaRobot
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

/**
 * [TwoFaTests] contains UI tests for 2FA flows.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltAndroidTest
class TwoFaTests {
    private val user = TestUser.twofa
    private val invalidCode = "123456"

    @get:Rule
    val rules = RuleChain
        .outerRule(ProtonHiltAndroidRule(this, TestApiConfig.Backend))
        .around(ActivityScenarioRule(MobileMainActivity::class.java))

    @Before
    fun setUp() {
        TestSetup.setCompletedOnboarding()
        TestSetup.quark?.jailUnban()
        AddAccountRobot()
            .signIn()
            .username(user.email)
            .password(user.password)
            .signIn<TwoFaRobot>()
    }

    @Test
    fun backToLogin() {
        TwoFaRobot()
            .verify { formElementsDisplayed() }
            .apply { Espresso.closeSoftKeyboard() }
            .back<AddAccountRobot>()
            .verify { addAccountElementsDisplayed() }
    }

    @Test
    fun revokeSessionOr3TimesInvalidCode() = runTest {
        TestSetup.quark!!.expireSession(user.email, true)
        TwoFaRobot()
            .setSecondFactorInput(invalidCode)
            .authenticate<AddAccountRobot>()
            .verify { addAccountElementsDisplayed() }
    }
}
