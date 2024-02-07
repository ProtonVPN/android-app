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

package com.protonvpn.tests.upgrade

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.rule.GrantPermissionRule
import com.protonvpn.test.shared.TestUser
import com.protonvpn.testRules.CommonRuleChains.realBackendComposeRule
import com.protonvpn.testRules.LoginTestRule
import com.protonvpn.testsHelper.TestSetup
import dagger.hilt.android.testing.HiltAndroidTest
import me.proton.core.domain.entity.AppStore
import me.proton.core.plan.test.MinimalUpgradeTests
import me.proton.core.test.quark.Quark
import org.junit.Before
import org.junit.Rule
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@HiltAndroidTest
class UpgradeTests : MinimalUpgradeTests {

    @get:Rule
    val activityRule = realBackendComposeRule()
        .around(LoginTestRule(TestUser.freeUser))
        .around(GrantPermissionRule.grant(android.Manifest.permission.POST_NOTIFICATIONS))

    override val quark: Quark = requireNotNull(TestSetup.quark)

    @Before
    fun setupPaymentMethods() {
        TestSetup.quark?.setPaymentMethods(AppStore.FDroid, card = true)
    }

    // TODO update with redesign home robot
    override fun startUpgrade() {
      //  HomeRobot()
      //      .verify { isInMainScreen() }
      //  HomeRobot()
      //      .setStateOfSecureCoreSwitch(true) // Force Upgrade flow.
      //      .verify { dialogUpgradeVisible() }
      //      .clickElementByText<HomeRobot>(R.string.upgrade)
    }
}
