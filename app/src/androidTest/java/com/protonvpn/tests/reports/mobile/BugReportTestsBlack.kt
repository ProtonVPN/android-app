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

package com.protonvpn.tests.reports.mobile

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.protonvpn.android.models.config.bugreport.Category
import com.protonvpn.android.models.config.bugreport.DynamicReportModel
import com.protonvpn.android.redesign.reports.ui.BugReportActivity
import com.protonvpn.android.utils.FileUtils
import com.protonvpn.android.utils.Storage
import com.protonvpn.interfaces.verify
import com.protonvpn.robots.mobile.RedesignBugReportRobot
import com.protonvpn.testRules.AppConfigRefreshTestRule
import com.protonvpn.testRules.CommonRuleChains.realBackendRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.serialization.builtins.ListSerializer
import me.proton.test.fusion.FusionConfig
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import kotlin.collections.orEmpty

@HiltAndroidTest
class BugReportTestsBlack {

    private val activityComposeRule = createAndroidComposeRule<BugReportActivity>()
        .also(FusionConfig.Compose.testRule::set)

    @get:Rule
    val ruleChain: RuleChain = realBackendRule()
        .around(AppConfigRefreshTestRule())
        .around(activityComposeRule)

    private lateinit var categories: List<Category>

    @Before
    fun setUp() {
        categories = Storage.load(DynamicReportModel::class.java)
            ?.categories
            .orEmpty()
    }

    @Test
    fun sendBugReportSuccess_whenCategoryHasSuggestions() {
        val category = categories.first()

        RedesignBugReportRobot
            .clickOnCategory(category = category)
            .clickOnContactUs()
            .fillReportForm(category = category)
            .clickOnSendReport()
            .verify { assertBugReportHasBeenSent() }
    }

    @Test
    fun sendBugReportSuccess_whenCategoryHasNoSuggestions() {
        val category = categories.last()

        RedesignBugReportRobot
            .clickOnCategory(category = category)
            .fillReportForm(category = category)
            .clickOnSendReport()
            .verify { assertBugReportHasBeenSent() }
    }

}
