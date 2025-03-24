/*
 * Copyright (c) 2021 Proton AG
 * This file is part of Proton AG and ProtonCore.
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

package com.protonvpn.testsTv.actions

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.matcher.ViewMatchers.withId
import com.protonvpn.base.BaseRobot
import com.protonvpn.android.R
import com.protonvpn.testsTv.verification.ConnectionVerify

/**
 * [TvDetailedCountryRobot] Contains all actions and verifications for country detail screen
 */
class TvDetailedCountryRobot : BaseRobot(){

    fun connectToStreamingCountry() : TvDetailedCountryRobot = clickElementById(R.id.connectStreaming)
    fun disconnectFromCountry() : TvDetailedCountryRobot = clickElementByText(R.string.disconnect)
    fun openServerList() : TvServerListRobot = clickElementByText(R.string.tv_server_list)
    fun addServerToFavourites() : TvDetailedCountryRobot = clickElementById(R.id.defaultConnection)
    fun goBackToCountryListView() : TvCountryListRobot = pressBack(R.id.container)
    fun getCountryName() : String = getText(onView(withId(R.id.countryName)))

    class Verify : ConnectionVerify(){
        fun userIsDisconnectedStreaming() = checkIfElementIsDisplayedById(R.id.connectStreaming)
    }

    inline fun verify(block: Verify.() -> Unit) = Verify().apply(block)
}