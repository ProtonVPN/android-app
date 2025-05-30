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

package com.protonvpn.tests.bugReport

import androidx.test.core.app.ActivityScenario
import com.protonvpn.android.appconfig.AppConfig
import com.protonvpn.android.models.config.bugreport.Category
import com.protonvpn.android.models.config.bugreport.DynamicReportModel
import com.protonvpn.android.models.config.bugreport.InputField
import com.protonvpn.android.ui.drawer.bugreport.DynamicReportActivity
import com.protonvpn.mocks.TestApiConfig
import com.protonvpn.robots.mobile.BugReportRobot
import com.protonvpn.interfaces.verify
import com.protonvpn.test.shared.TestUser
import com.protonvpn.testRules.ProtonHiltAndroidRule
import com.protonvpn.testRules.SetLoggedInUserRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import javax.inject.Inject

@HiltAndroidTest
class BugReportMockedTests {
    @Inject
    lateinit var appConfig: AppConfig

    private val user = TestUser.plusUser
    private val inputFieldList: List<InputField> =
        listOf(InputField("Problem:", "Problem", true, "submit", type = "TextSingleLine"))
    private val dynamicReportModel: List<Category> =
        listOf(Category(inputFieldList, "VPN Not working", "Send", emptyList()))

    private val mockApiConfig = TestApiConfig.Mocked(user) {
        rule(get, path eq "/vpn/v1/featureconfig/dynamic-bug-reports") {
            respond(DynamicReportModel(dynamicReportModel))
        }
    }

    private val hiltRule = ProtonHiltAndroidRule(this, mockApiConfig)
    private lateinit var bugCategory: Category

    @get:Rule
    var rules = RuleChain
        .outerRule(hiltRule)
        .around(SetLoggedInUserRule(user))

    @Before
    fun setUp() {
        bugCategory = dynamicReportModel.first()
        hiltRule.inject()
        runBlocking { appConfig.forceUpdate(user.vpnUser.userId) }
        ActivityScenario.launch(DynamicReportActivity::class.java)
    }

    @Test
    fun bugReportWhenApiReturnsSuggestionsEmptyList() {
        BugReportRobot.verify { bugTypesAreShown(dynamicReportModel) }
            .selectCategory(bugCategory.label)
            .verify { inputFieldsAreDisplayed(bugCategory) }
    }

    @Test
    fun bugReportWhenMandatoryInputFieldIsNotFilled() {
        BugReportRobot.verify { bugTypesAreShown(dynamicReportModel) }
            .selectCategory(bugCategory.label)
            .sendReport()
            .verify { mandatoryFieldErrorIsDisplayed() }
    }
}
