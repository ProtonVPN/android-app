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

import androidx.test.platform.app.InstrumentationRegistry
import com.protonvpn.android.R
import com.protonvpn.base.BaseRobot
import com.protonvpn.base.BaseVerify

class UpsellModalRobot : BaseRobot() {
    fun closeModal() : UpsellModalRobot = clickElementByText(R.string.close)
    class Verify : BaseVerify() {
        fun profilesUpsellIsShown() {
            checkIfElementIsDisplayedByStringId(R.string.upgrade_profiles_title)
            checkIfElementIsDisplayedByStringId(R.string.upgrade_profiles_text)
            checkIfElementIsDisplayedByStringId(R.string.upgrade_profiles_feature_save)
            checkIfElementIsDisplayedByStringId(R.string.upgrade_profiles_feature_customize)
            checkIfElementIsDisplayedByStringId(R.string.close)
        }

        fun specificCountryUpsellIsShown(speed: Int) {
            val upsellSpeedString = InstrumentationRegistry.getInstrumentation().targetContext.getString(
                R.string.upgrade_country_feature_speed, speed
            )
            checkIfElementIsDisplayedByStringId(R.string.upgrade_country_title)
            checkIfElementIsDisplayedByStringId(R.string.upgrade_country_message)
            checkIfElementIsDisplayedByText(upsellSpeedString)
            checkIfElementIsDisplayedByStringId(R.string.upgrade_country_feature_stream)
            checkIfElementIsDisplayedByStringId(R.string.upgrade_country_feature_money_back)
        }

        fun countryUpsellIsShown(){
            checkIfElementIsDisplayedByStringId(R.string.upgrade_plus_subtitle)
            checkIfElementIsDisplayedByStringId(R.string.upgrade_plus_countries_choose_location)
            checkIfElementIsDisplayedByStringId(R.string.upgrade_plus_countries_even_higher_speed)
            checkIfElementIsDisplayedByStringId(R.string.upgrade_plus_countries_access_content)
            checkIfElementIsDisplayedByStringId(R.string.upgrade_plus_countries_stream)
        }
    }

    inline fun verify(block: Verify.() -> Unit) = Verify().apply(block)
}
