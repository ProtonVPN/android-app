/*
 * Copyright (c) 2025 Proton AG
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

package com.protonvpn.tests.reports.tv

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.protonvpn.android.models.config.bugreport.Category
import com.protonvpn.android.models.config.bugreport.DynamicReportModel
import com.protonvpn.android.models.config.bugreport.InputField
import com.protonvpn.android.tv.reports.TvBugReportActivity
import com.protonvpn.interfaces.verify
import com.protonvpn.mocks.TestApiConfig
import com.protonvpn.robots.tv.TvBugReportRobot
import com.protonvpn.testRules.AppConfigRefreshTestRule
import com.protonvpn.testRules.ProtonHiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import me.proton.core.network.data.protonApi.GenericResponse
import me.proton.core.network.domain.ResponseCodes
import me.proton.test.fusion.FusionConfig
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

@HiltAndroidTest
class TvBugReportTestsMocked {

    private val activityComposeRule = createAndroidComposeRule<TvBugReportActivity>()
        .also(FusionConfig.Compose.testRule::set)

    private val categories: List<Category> = listOf(
        Category(
            label = "Something else",
            submitLabel = "categorySubmitLabel",
            suggestions = emptyList(),
            inputFields = listOf(
                InputField(
                    label = "What went wrong?",
                    placeholder = "Describe the problem in as much detail as you can. If there was an error message, let us know what it said.",
                    submitLabel = "inputFieldSubmitLabel",
                    type = "TextMultiLine",
                ),
            ),
        ),
    )

    private val hiltRule = ProtonHiltAndroidRule(
        testInstance = this,
        apiConfig = TestApiConfig.Mocked {
            rule(get, path eq "/vpn/v1/featureconfig/dynamic-bug-reports") {
                respond(serializableObject = DynamicReportModel(categories = categories))
            }

            rule(post, path eq "/core/v4/reports/bug") {
                respond(serializableObject = GenericResponse(code = ResponseCodes.OK))
            }
        },
    )

    @get:Rule
    val ruleChain: RuleChain = RuleChain
        .outerRule(hiltRule)
        .around(AppConfigRefreshTestRule())
        .around(activityComposeRule)

    @Test
    fun sendBugReportSuccess() {
        val categoryIndex = 0
        val category = categories[categoryIndex]

        TvBugReportRobot
            .clickOnCategory(category = category, categoryIndex = categoryIndex)
            .fillReportForm(category = category, activity = activityComposeRule.activity)
            .clickOnSendReport()
            .verify { assertBugReportHasBeenSent() }
    }

}
