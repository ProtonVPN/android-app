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

package com.protonvpn.actions

import com.protonvpn.android.R
import com.protonvpn.android.models.config.bugreport.Category
import com.protonvpn.base.BaseRobot
import com.protonvpn.base.BaseVerify
import com.protonvpn.matchers.ProtonMatcher.inputFieldByLabel
import me.proton.core.presentation.R as CoreR

class BugReportRobot : BaseRobot() {
    fun selectCategory(categoryName: String): BugReportRobot = clickElementByText(categoryName)

    fun contactUs(): BugReportRobot = clickElementById(R.id.buttonContactUs)

    fun sendReport(): BugReportRobot {
        view.withId(R.id.buttonReport).click()
        return this
    }

    fun fillData(category: Category, email: String): BugReportRobot {
        replaceText<BugReportRobot>(R.id.editEmail, email)
        category.inputFields.forEach {
            view.withCustomMatcher(inputFieldByLabel(it.label))
                .withId(CoreR.id.input)
                .replaceText("Ignore This Report")
        }
        return this
    }

    class Verify : BaseVerify() {
        fun bugReportIsSent() {
            checkIfElementIsDisplayedById(R.id.image)
            checkIfElementIsDisplayedById(R.id.backButton)
            checkIfElementIsDisplayedByStringId(R.string.dynamic_report_success_title)
            checkIfElementIsDisplayedByStringId(R.string.dynamic_report_success_description)
        }

        fun suggestionsAreShown(category: Category) {
            category.suggestions.forEach {
                checkIfElementIsDisplayedByText(it.text)
            }
        }

        fun bugTypesAreShown(category: List<Category>) {
            category.forEach {
                checkIfElementIsDisplayedByText(it.label)
            }
        }

        fun inputFieldsAreDisplayed(category: Category) {
            category.inputFields.forEach {
                checkIfElementIsDisplayedByContentDesc(it.label)
            }
        }

        fun mandatoryFieldErrorIsDisplayed() =
            checkIfElementIsDisplayedByStringId(R.string.dynamic_report_field_mandatory)
    }

    inline fun verify(block: Verify.() -> Unit) = Verify().apply(block)
}
