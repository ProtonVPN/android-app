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

package com.protonvpn.robots.mobile

import com.protonvpn.android.R
import com.protonvpn.android.models.config.bugreport.Category
import com.protonvpn.interfaces.Robot
import me.proton.test.fusion.Fusion.node

object RedesignBugReportRobot : Robot {

    fun assertBugReportHasBeenSent(): RedesignBugReportRobot {
        node.withText(textId = R.string.report_sent)
            .await { assertIsDisplayed() }

        return this
    }

    fun clickOnCategory(category: Category): RedesignBugReportRobot {
        node.withText(text = category.label)
            .scrollTo()
            .click()

        return this
    }

    fun clickOnContactUs(): RedesignBugReportRobot {
        node.withText(textId = R.string.dynamic_report_contact_us)
            .click()

        return this
    }

    fun clickOnSendReport(): RedesignBugReportRobot {
        node.withText(textId = R.string.send_report)
            .click()

        return this
    }

    fun fillReportForm(
        category: Category,
        email: String = "test@email.com",
    ): RedesignBugReportRobot {
        val reportForm = node.withTag(tag = "BugReportForm")

        node.withText(textId = R.string.report_bug_email_label)
            .replaceText(text = email)

        category.inputFields.forEachIndexed { index, inputField ->
            reportForm.scrollToIndex(index = index)

            node.withText(text = inputField.label)
                .replaceText(text = "${category.label} $index")
        }

        return this
    }

}
