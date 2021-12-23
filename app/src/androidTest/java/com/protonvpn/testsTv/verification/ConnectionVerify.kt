/*
 * Copyright (c) 2021 Proton Technologies AG
 * This file is part of Proton Technologies AG and ProtonCore.
 *
 * ProtonCore is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * ProtonCore is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with ProtonCore.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.protonvpn.testsTv.verification

import com.protonvpn.android.R
import com.protonvpn.base.BaseVerify
import me.proton.core.test.android.instrumented.ui.espresso.OnView
import org.strongswan.android.logic.StrongSwanApplication.getContext

/**
 * [ConnectionVerify] Contains common verification methods for connection
 */
open class ConnectionVerify : BaseVerify() {
    fun userIsConnected(): OnView = checkIfElementIsDisplayedByStringId(R.string.disconnect)
    fun userIsDisconnected(): OnView = checkIfElementIsNotDisplayedByStringId(R.string.disconnect)
    fun connectionStatusDidNotChange(status: String): OnView = checkIfElementByIdContainsText(R.id.textStatus, status)

    fun userIsConnectedToCorrectCountry(country: String) {
        val connectedToValue = String.format(getContext().getString(R.string.stateConnectedTo), country)
        checkIfElementByIdContainsText(R.id.textStatus, connectedToValue)
    }
}
