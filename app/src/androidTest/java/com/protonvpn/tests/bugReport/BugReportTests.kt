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

package com.protonvpn.tests.bugReport

import com.protonvpn.actions.BugReportRobot
import com.protonvpn.actions.HomeRobot
import com.protonvpn.android.api.ProtonApiRetroFit
import com.protonvpn.android.models.config.bugreport.Category
import com.protonvpn.mocks.TestApiConfig
import com.protonvpn.test.shared.TestUser
import com.protonvpn.testRules.LoginTestRule
import com.protonvpn.testRules.ProtonHiltAndroidRule
import com.protonvpn.testRules.ProtonHomeActivityTestRule
import com.protonvpn.testsHelper.TestSetup
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import javax.inject.Inject

@HiltAndroidTest
class BugReportTests {

    private val email = "testing@mail.com"
    private val hiltRule = ProtonHiltAndroidRule(this, TestApiConfig.Backend)
    private val testRule = ProtonHomeActivityTestRule()
    private lateinit var homeRobot: HomeRobot
    private lateinit var reportBugRobot: BugReportRobot
    private lateinit var category: Category
    private lateinit var categories: List<Category>

    @Inject
    lateinit var api: ProtonApiRetroFit

    @get:Rule
    val rules = RuleChain
        .outerRule(ProtonHiltAndroidRule(this, TestApiConfig.Backend))
        .around(LoginTestRule(TestUser.plusUser))
        .around(testRule)

    @Before
    fun setUp() {
        TestSetup.setCompletedOnboarding()
        hiltRule.inject()
        homeRobot = HomeRobot()
        reportBugRobot = BugReportRobot()
        categories = runBlocking { api.getDynamicReportConfig().valueOrThrow.categories }
        category = categories[0]
    }

    @Test
    fun bugReportHappyPath() {
        homeRobot.clickOnDrawerMenuReportBugOption()
            .verify { bugTypesAreShown(categories) }
        reportBugRobot.selectCategory(category.label)
            .verify { suggestionsAreShown(category) }
        reportBugRobot.contactUs()
            .verify { inputFieldsAreDisplayed(category) }
        reportBugRobot.fillData(category, email)
            .sendReport()
            .verify { bugReportIsSent() }
    }
}