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

import com.protonvpn.base.BaseRobot
import com.protonvpn.android.R

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

    class Verify : ConnectionVerify()

    inline fun verify(block: Verify.() -> Unit) = Verify().apply(block)
}