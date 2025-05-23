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

package com.protonvpn.tests.login.mobile

import com.protonvpn.robots.mobile.HomeRobot
import com.protonvpn.interfaces.verify
import com.protonvpn.test.shared.TestUser
import com.protonvpn.testRules.CommonRuleChains.realBackendComposeRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import me.proton.core.auth.test.flow.SignInFlow
import me.proton.core.auth.test.robot.AddAccountRobot
import me.proton.core.test.quark.Quark
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject

/**
 * [TwoPassTestsBlack] contains UI tests for TwoPass flows.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltAndroidTest
class TwoPassTestsBlack {

    @get:Rule
    val rule = realBackendComposeRule()

    @Inject lateinit var quark: Quark

    private val user = TestUser.twopass

    @Before
    fun setUp() {
        quark.jailUnban()
    }

    @Test
    fun signInTwoPassDoNotShowTwoPassScreen() = runTest {
        AddAccountRobot.clickSignIn()
        SignInFlow.signInInternal(user.email, user.password)
        HomeRobot.verify { isLoggedIn() }
    }
}
