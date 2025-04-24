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

import com.protonvpn.interfaces.Robot
import com.protonvpn.android.R
import me.proton.test.fusion.Fusion.node

object ConnectionPanelRobot : Robot {
    private val backButton get() = node.withContentDescription(R.string.accessibility_back)

    fun goBack(): ConnectionPanelRobot = backButton.clickTo(this)

    fun correctIpIsDisplayed(ip: String) = nodeWithTextDisplayed(ip)
    fun correctProtocolIsDisplayed(protocol: Int) =
        node.withText(protocol).scrollTo().await { assertIsDisplayed() }
}