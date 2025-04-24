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

package com.protonvpn.robots.mobile

import com.protonvpn.android.R
import com.protonvpn.android.models.config.bugreport.Category
import com.protonvpn.matchers.ProtonMatcher.inputFieldByLabel
import com.protonvpn.interfaces.Robot
import me.proton.test.fusion.Fusion.view

object BugReportRobot : Robot {
    private val bugReportButton get() = view.withId(R.id.buttonReport)
    private val contactUsButton get() = view.withId(R.id.buttonContactUs)
    private val emailInput get() = view.withId(R.id.editEmail)
    private val image get() = view.withId(R.id.image)
    private val backButton get() = view.withId(R.id.backButton)
    private val bugReportSuccessTitle get() = view.withText(R.string.dynamic_report_success_title)
    private val bugReportSuccessDescription get() = view.withText(R.string.dynamic_report_success_description)
    private val bugReportMandatoryFieldError get() = view.withText(R.string.dynamic_report_field_mandatory)

    fun selectCategory(categoryName: String): BugReportRobot {
        view.withText(categoryName).click()
        return this
    }

    fun contactUs(): BugReportRobot {
        contactUsButton.click()
        return this
    }

    fun sendReport(): BugReportRobot {
        bugReportButton.click()
        return this
    }

    fun fillData(category: Category, email: String): BugReportRobot {
        emailInput.replaceText(email)
        category.inputFields.forEach {
            view.withCustomMatcher(inputFieldByLabel(it.label))
                .withId(me.proton.core.presentation.R.id.input)
                .replaceText("Ignore This Report")
        }
        return this
    }

    fun bugReportIsSent() : BugReportRobot {
        image.checkIsDisplayed()
        backButton.checkIsDisplayed()
        bugReportSuccessTitle.checkIsDisplayed()
        bugReportSuccessDescription.checkIsDisplayed()
        return this
    }

    fun suggestionsAreShown(category: Category) : BugReportRobot {
        category.suggestions.forEach {
            view.withText(it.text).checkIsDisplayed()
        }
        return this
    }

    fun bugTypesAreShown(category: List<Category>) : BugReportRobot {
        category.forEach {
            view.withText(it.label).checkIsDisplayed()
        }
        return this
    }

    fun inputFieldsAreDisplayed(category: Category) : BugReportRobot {
        category.inputFields.forEach {
            view.withContentDesc(it.label).checkIsDisplayed()
        }
        return this
    }

    fun mandatoryFieldErrorIsDisplayed() : BugReportRobot {
        bugReportMandatoryFieldError.checkIsDisplayed()
        return this
    }
}