/*
 *  Copyright (c) 2021 Proton AG
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

import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.protonvpn.actions.AddAccountRobot
import com.protonvpn.actions.HomeRobot
import com.protonvpn.actions.LoginRobot
import com.protonvpn.android.ui.main.MobileMainActivity
import com.protonvpn.annotations.TestID
import com.protonvpn.testRules.ProtonHiltAndroidRule
import com.protonvpn.test.shared.TestUser
import com.protonvpn.testsHelper.TestSetup
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/**
 * [LoginTests] contains UI tests for Login flow
 */
@LargeTest
@RunWith(AndroidJUnit4::class)
@HiltAndroidTest
class LoginTests {

    private lateinit var addAccountRobot: AddAccountRobot
    private lateinit var loginRobot: LoginRobot
    private lateinit var homeRobot: HomeRobot
    private val activityRule = ActivityScenarioRule(MobileMainActivity::class.java)

    @get:Rule
    val rules = RuleChain
        .outerRule(ProtonHiltAndroidRule(this))
        .around(activityRule)

    @Before
    fun setUp() {
        TestSetup.setCompletedOnboarding()
        addAccountRobot = AddAccountRobot()
        loginRobot = LoginRobot()
        homeRobot = HomeRobot()
        addAccountRobot.selectSignInOption()
    }

    @Test
    @TestID(51)
    fun loginWithPlusUser(){
        loginRobot.signIn(TestUser.plusUser)
            .verify { successfullyLoggedIn() }
    }

    @Test
    @TestID(52)
    fun loginWithIncorrectCredentials() {
        loginRobot.signInWithIncorrectCredentials()
            .verify { incorrectLoginCredentialsIsShown() }
    }

    @Test
    @TestID(103957)
    fun viewPasswordIsVisible() {
        loginRobot.enterCredentials(TestUser.plusUser)
            .viewPassword()
            .verify { passwordIsVisible(TestUser.plusUser) }
    }

    @Test
    @TestID(103958)
    fun needHelpMenuIsOpened() {
        loginRobot.selectNeedHelp()
            .verify { needHelpOptionsAreDisplayed() }
    }

    @Ignore //Remove when https://jira.protontech.ch/browse/VPNAND-705 will be done
    @Test
    @TestID(103959)
    fun rememberMeFunctionality() {
        loginRobot.signIn(TestUser.plusUser)
            .verify { successfullyLoggedIn() }
        homeRobot.logout()
            .selectSignInOption()
            .verify { userNameIsVisible(TestUser.plusUser) }
    }

    @Test
    @TestID(53)
    fun loginWithSpecialCharsUser(){
        loginRobot.signIn(TestUser.specialCharUser)
            .verify { successfullyLoggedIn() }
    }
}
