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

package com.protonvpn.actions

import com.protonvpn.android.R
import com.protonvpn.base.BaseRobot
import com.protonvpn.base.BaseVerify
import me.proton.test.fusion.Fusion.node

class UpsellModalRobot : BaseRobot() {

    fun closeModal() : UpsellModalRobot {
        node.withText(R.string.close).click()
        return this
    }

    class Verify : BaseVerify() {
        fun profilesUpsellIsShown() {
            checkIfElementIsDisplayedByStringId(R.string.upgrade_profiles_title)
            checkIfElementIsDisplayedByHtmlStringId(R.string.upgrade_profiles_text)
            node.withText(R.string.close).assertIsDisplayed()
        }

        fun specificCountryUpsellIsShown() {
            checkIfElementIsDisplayedByStringId(R.string.upgrade_country_title)
            checkIfElementIsDisplayedByHtmlStringId(R.string.upgrade_country_message)
        }

        fun countryUpsellIsShown() {
            checkIfElementIsDisplayedByStringId(R.string.upgrade_all_countries_title)
        }
    }

    inline fun verify(block: Verify.() -> Unit) = Verify().apply(block)
}
