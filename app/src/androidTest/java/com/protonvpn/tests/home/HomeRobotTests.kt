/*
 * Copyright (c) 2018 Proton Technologies AG
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
package com.protonvpn.tests.home

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.protonvpn.actions.HomeRobot
import com.protonvpn.di.MockApi
import com.protonvpn.tests.testRules.ProtonHomeActivityTestRule
import com.protonvpn.tests.testRules.SetUserPreferencesRule
import com.protonvpn.test.shared.TestUser
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
class HomeRobotTests {

    private val homeRobot = HomeRobot()

    @get:Rule
    var testRule = ProtonHomeActivityTestRule()

    @Test
    fun offerShown() {
        homeRobot.openDrawer()
        homeRobot.checkOfferVisible(MockApi.OFFER_LABEL)
        homeRobot.checkOfferNotVisible(MockApi.PAST_OFFER_LABEL)
        homeRobot.checkOfferNotVisible(MockApi.FUTURE_OFFER_LABEL)
    }

    companion object {
        @ClassRule
        @JvmField
        var testClassRule = SetUserPreferencesRule(TestUser.getPlusUser())
    }
}
