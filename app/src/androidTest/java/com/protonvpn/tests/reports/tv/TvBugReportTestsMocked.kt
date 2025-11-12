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
import com.protonvpn.android.tv.reports.TvBugReportActivity
import com.protonvpn.android.utils.FileUtils
import com.protonvpn.android.utils.Storage
import com.protonvpn.interfaces.verify
import com.protonvpn.mocks.TestApiConfig
import com.protonvpn.robots.tv.TvBugReportRobot
import com.protonvpn.testRules.ProtonHiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.ListSerializer
import me.proton.core.network.data.protonApi.GenericResponse
import me.proton.core.network.domain.ResponseCodes
import me.proton.test.fusion.FusionConfig
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

@HiltAndroidTest
class TvBugReportTestsMocked {

    private val activityComposeRule = createAndroidComposeRule<TvBugReportActivity>()
        .also(FusionConfig.Compose.testRule::set)

    private val hiltRule = ProtonHiltAndroidRule(
        testInstance = this,
        apiConfig = TestApiConfig.Mocked {
            rule(post, path eq "/core/v4/reports/bug") {
                respond(serializableObject = GenericResponse(code = ResponseCodes.OK))
            }
        },
    )

    @get:Rule
    val ruleChain: RuleChain = RuleChain
        .outerRule(hiltRule)
        .around(activityComposeRule)

    private lateinit var categories: List<Category>

    @Before
    fun setUp() {
        categories = Storage.load(DynamicReportModel::class.java)
            ?.categories
            .orEmpty()
    }

    @Test
    fun sendBugReportSuccess() {
        val categoryIndex = categories.size.minus(1)
        val category = categories[categoryIndex]

        TvBugReportRobot
            .clickOnCategory(category = category, categoryIndex = categoryIndex)
            .fillReportForm(category = category, activity = activityComposeRule.activity)
            .clickOnSendReport()
            .verify { assertBugReportHasBeenSent() }
    }

}
