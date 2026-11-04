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

package com.protonvpn.robots.tv

import androidx.test.espresso.matcher.ViewMatchers
import com.protonvpn.android.R
import com.protonvpn.base.BaseRobot
import com.protonvpn.base.BaseVerify

/**
 * [TvServerListRobot] Contains all actions and verifications for server list view
 */
class TvServerListRobot : BaseRobot() {

    fun disconnectFromServer() : TvServerListRobot = clickElementByText(R.string.disconnect)

    fun connectToServer() : TvServerListRobot {
        recyclerView
                .withId(R.id.row_content)
                .onItemAtPosition(0)
                .click()
        return TvServerListRobot()
    }

    class Verify : BaseVerify() {
        fun disconnectButtonIsDisplayed() {
            visibleDisconnectButton().checkDisplayed()
        }

        // There are multiple servers with a "Connect" button, check that none is connected instead.
        fun disconnectButtonIsNotDisplayed() {
            visibleDisconnectButton().checkDoesNotExist()
        }

        // The hierarchy contains an invisible copy of the connected card, match only the visible one.
        private fun visibleDisconnectButton() =
            view.withId(R.id.actionButton)
                .withVisibility(ViewMatchers.Visibility.VISIBLE)
                .withText(R.string.disconnect)
    }

    inline fun verify(block: Verify.() -> Unit) = Verify().apply(block)
}