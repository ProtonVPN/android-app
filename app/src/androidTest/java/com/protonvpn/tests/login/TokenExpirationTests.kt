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

package com.protonvpn.tests.login

import com.protonvpn.actions.LoginRobot
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.data.Timeouts
import com.protonvpn.test.shared.TestUser
import com.protonvpn.testRules.CommonRuleChains.realBackendComposeRule
import com.protonvpn.testRules.LoginTestRule
import com.protonvpn.testsHelper.TestSetup
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.test.runTest
import me.proton.core.accountmanager.data.SessionManagerImpl
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject
import kotlin.test.assertNotNull

//TODO: Adapt this test case to redesign when more options to trigger API call will be given
@HiltAndroidTest
class TokenExpirationTests {

    private lateinit var loginRobot: LoginRobot

    @get:Rule
    val rule = realBackendComposeRule()
        .around(LoginTestRule(TestUser.plusUser))

    @Inject lateinit var currentUser: CurrentUser

    @Before
    fun setUp() {
        assertNotNull(
            TestSetup.quark,
            "Quark can be null. Make sure this test is not being run on production."
        )
        loginRobot = LoginRobot()
    }

    @Test
    fun sessionAndRefreshTokenExpiration() = runTest(timeout = Timeouts.TWENTY_SECONDS) {
        SessionManagerImpl.reset(currentUser.sessionId())
        TestSetup.quark!!.expireSession(TestUser.plusUser.email, true)
        loginRobot.verify { loginScreenIsDisplayed() }
    }

    @Test
    fun sessionExpirationCheckIfNotLoggedOut() = runTest(timeout = Timeouts.TWENTY_SECONDS) {
        SessionManagerImpl.reset(currentUser.sessionId())
        TestSetup.quark!!.expireSession(TestUser.plusUser.email)
    }
}
