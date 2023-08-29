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

package com.protonvpn.testRules

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.protonvpn.android.ui.main.MobileMainActivity
import com.protonvpn.mocks.TestApiConfig
import com.protonvpn.test.shared.TestUser
import me.proton.test.fusion.FusionConfig
import org.junit.rules.RuleChain

object CommonRuleChains {
    fun Any.mockedLoggedInRule(
        testUser: TestUser = TestUser.plusUser,
        mockedBackend: TestApiConfig = TestApiConfig.Mocked(testUser)
    ): RuleChain {
        val hiltRule = ProtonHiltAndroidRule(this, mockedBackend)
        val composeRule = createAndroidComposeRule<MobileMainActivity>()
        FusionConfig.Compose.testRule.set(composeRule)

        return RuleChain
            .outerRule(hiltRule)
            .around(SetLoggedInUserRule(testUser))
            .around(ProtonHiltInjectRule(hiltRule))
            .around(composeRule)
    }
}
