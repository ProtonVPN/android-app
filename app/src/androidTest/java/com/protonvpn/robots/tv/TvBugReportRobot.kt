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

package com.protonvpn.robots.tv

import android.app.Activity
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.protonvpn.android.R
import com.protonvpn.android.models.config.bugreport.Category
import com.protonvpn.interfaces.Robot
import com.protonvpn.interfaces.waitUntilKeyboardVisibility
import me.proton.test.fusion.Fusion.node

object TvBugReportRobot : Robot {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    private val uiDevice = UiDevice.getInstance(instrumentation)

    fun assertBugReportHasBeenSent(): TvBugReportRobot {
        node.withText(textId = R.string.report_sent)
            .await { assertIsDisplayed() }

        return this
    }

    fun clickOnCategory(category: Category, categoryIndex: Int): TvBugReportRobot {
        ensureCategoryIsDisplayed(category = category)

        repeat(times = categoryIndex) {
            uiDevice.pressDPadDown()
        }

        uiDevice.pressDPadCenter()

        return this
    }

    fun clickOnContactUs(category: Category): TvBugReportRobot {
        ensureCategoryIsDisplayed(category = category)

        repeat(times = category.suggestions.size) {
            uiDevice.pressDPadDown()
        }

        uiDevice.pressDPadCenter()

        return this
    }

    fun fillReportForm(
        category: Category,
        activity: Activity,
        email: String = "test@email.com",
    ): TvBugReportRobot {
        ensureCategoryIsDisplayed(category = category)

        waitUntilKeyboardVisibility(activity = activity, isVisible = true)

        node.withText(textId = R.string.report_bug_email_label)
            .replaceText(text = email)
            .performImeAction()

        category.inputFields.forEachIndexed { index, inputField ->
            when (inputField.type) {
                "TextSingleLine",
                "TextMultiLine" -> {
                    node.withText(text = inputField.label)
                        .replaceText(text = "${category.label} $index")
                        .performImeAction()
                }

                "Dropdown" -> {
                    uiDevice.pressDPadCenter()

                    node.withText(text = inputField.dropdownOptions.last().label).await { assertIsDisplayed() }

                    uiDevice.pressDPadCenter()

                    node.withText(text = inputField.dropdownOptions.first().label).await { assertIsDisplayed() }
                }
            }

        }

        waitUntilKeyboardVisibility(activity = activity, isVisible = false)

        // Moves focus to attach LOGs checkbox first and then to submit button
        repeat(times = 2) {
            uiDevice.pressDPadDown()
        }

        return this
    }

    fun clickOnSendReport(): TvBugReportRobot {
        node.withText(textId = R.string.send_report).await { assertIsDisplayed() }

        uiDevice.pressDPadCenter()

        return this
    }

    // This is to prevent DPad events being consumed before the UI is ready
    private fun ensureCategoryIsDisplayed(category: Category) {
        node.withText(text = category.label).await { assertIsDisplayed() }
    }

}
