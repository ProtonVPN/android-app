/*
 *
 *  * Copyright (c) 2025. Proton AG
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

import androidx.test.core.app.ApplicationProvider
import com.protonvpn.android.ProtonApplication
import com.protonvpn.android.R
import com.protonvpn.base.BaseVerify
import me.proton.test.fusion.Fusion.node

/**
 * [ConnectionVerify] Contains common verification methods for connection
 */
open class ConnectionVerify : BaseVerify() {
    fun userIsConnected() {
        if (isTvMainScreenRedesign()) {
            node.withText(R.string.disconnect).await { assertIsDisplayed() }
        } else {
            checkIfElementIsDisplayedByStringId(R.string.disconnect)
        }
    }

    fun userIsDisconnected() {
        if (isTvMainScreenRedesign()) {
            node.withText(R.string.tv_vpn_status_action_connect_fastest).await { assertIsDisplayed() }
        } else {
            checkIfElementIsNotDisplayedByStringId(R.string.disconnect)
        }
    }

    fun connectionStatusDidNotChange(status: String) {
        if (isTvMainScreenRedesign()) {
            node.withText(status).await { assertIsDisplayed() }
        } else {
            checkIfElementByIdContainsText(R.id.textStatus, status)
        }
    }

    fun userIsConnectedToCorrectCountry(country: String) {
        val appContext = ApplicationProvider.getApplicationContext<ProtonApplication>()
        val connectedToValue = String.format(appContext.getString(R.string.stateConnectedTo), country)
        checkIfElementByIdContainsText(R.id.textStatus, connectedToValue)
    }
}
