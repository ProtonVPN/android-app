/*
 * Copyright (c) 2023. Proton AG
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

package com.protonvpn.tests.profiles

import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.filters.LargeTest
import com.protonvpn.actions.HomeRobot
import com.protonvpn.actions.ProfilesRobot
import com.protonvpn.android.ui.home.HomeActivity
import com.protonvpn.annotations.TestID
import com.protonvpn.data.DefaultData
import com.protonvpn.mocks.TestApiConfig
import com.protonvpn.test.shared.TestUser
import com.protonvpn.testRules.ProtonHiltAndroidRule
import com.protonvpn.testRules.SetLoggedInUserRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

@LargeTest
@HiltAndroidTest
class ProfileFreeUserTests {

    @get:Rule
    var rules = RuleChain
        .outerRule(ProtonHiltAndroidRule(this, TestApiConfig.Mocked(TestUser.freeUser)))
        .around(SetLoggedInUserRule(TestUser.freeUser))
        .around(ActivityScenarioRule(HomeActivity::class.java))

    private lateinit var homeRobot: HomeRobot
    private lateinit var profilesRobot: ProfilesRobot

    @Before
    fun setup() {
        homeRobot = HomeRobot()
        profilesRobot = ProfilesRobot()
    }

    @Test
    @TestID(103987)
    fun tryToCreateProfileWithFreeUser() {
        homeRobot.swipeLeftToOpenProfiles()
        profilesRobot.clickOnCreateNewProfileButton()
            .insertTextInProfileNameField(DefaultData.PROFILE_NAME)
            .verify { upgradeButtonIsDisplayed() }
    }
}
