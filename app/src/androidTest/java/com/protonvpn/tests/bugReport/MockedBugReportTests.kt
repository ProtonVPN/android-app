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

import androidx.test.core.app.ActivityScenario
import com.protonvpn.actions.BugReportRobot
import com.protonvpn.actions.HomeRobot
import com.protonvpn.android.models.config.bugreport.Category
import com.protonvpn.android.models.config.bugreport.DynamicReportModel
import com.protonvpn.android.models.config.bugreport.InputField
import com.protonvpn.android.ui.drawer.bugreport.DynamicReportActivity
import com.protonvpn.mocks.TestApiConfig
import com.protonvpn.test.shared.TestUser
import com.protonvpn.testRules.ProtonHiltAndroidRule
import com.protonvpn.testRules.SetLoggedInUserRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

@HiltAndroidTest
class MockedBugReportTests {

    private val inputFieldList: List<InputField> =
        listOf(InputField("Problem:", "Problem", true, "submit", type = "TextSingleLine"))
    private val dynamicReportModel: List<Category> =
        listOf(Category(inputFieldList, "VPN Not working", "Send", emptyList()))

    private val mockApiConfig = TestApiConfig.Mocked(TestUser.plusUser) {
        rule(get, path eq "/vpn/v1/featureconfig/dynamic-bug-reports") {
            respond(DynamicReportModel(dynamicReportModel))
        }
    }

    private val hiltRule = ProtonHiltAndroidRule(this, mockApiConfig)
    private lateinit var bugCategory: Category
    private lateinit var reportBugRobot: BugReportRobot
    private lateinit var homeRobot: HomeRobot

    @get:Rule
    var rules = RuleChain
        .outerRule(hiltRule)
        .around(SetLoggedInUserRule(TestUser.plusUser))

    @Before
    fun setUp() {
        reportBugRobot = BugReportRobot()
        homeRobot = HomeRobot()
        bugCategory = dynamicReportModel.first()
        hiltRule.inject()
        ActivityScenario.launch(DynamicReportActivity::class.java)
    }

    @Test
    fun bugReportWhenApiReturnsSuggestionsEmptyList() {
        reportBugRobot.verify { bugTypesAreShown(dynamicReportModel) }
        reportBugRobot.selectCategory(bugCategory.label)
            .verify { inputFieldsAreDisplayed(bugCategory) }
    }

    @Test
    fun bugReportWhenMandatoryInputFieldIsNotFilled() {
        reportBugRobot.verify { bugTypesAreShown(dynamicReportModel) }
        reportBugRobot.selectCategory(bugCategory.label)
            .sendReport()
            .verify { mandatoryFieldErrorIsDisplayed() }
    }
}
