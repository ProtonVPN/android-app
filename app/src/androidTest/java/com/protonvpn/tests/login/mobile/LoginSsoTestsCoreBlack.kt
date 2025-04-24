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

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.protonvpn.robots.mobile.HomeRobot
import com.protonvpn.interfaces.verify
import com.protonvpn.android.appconfig.AppConfig
import com.protonvpn.testRules.CommonRuleChains.realBackendComposeRule
import dagger.hilt.android.testing.HiltAndroidTest
import me.proton.core.auth.test.MinimalSignInExternalTests
import org.junit.Ignore
import org.junit.Rule
import org.junit.runner.RunWith
import javax.inject.Inject

@LargeTest
@RunWith(AndroidJUnit4::class)
@HiltAndroidTest
@Ignore("TODO: Update SSO credentials.")
class LoginSsoTestsCoreBlack : MinimalSignInExternalTests {

    @get:Rule
    val rule = realBackendComposeRule()

    @Inject
    lateinit var appConfig: AppConfig

    override fun verifyAfter() {
        HomeRobot.verify { isLoggedIn() }
    }
}
