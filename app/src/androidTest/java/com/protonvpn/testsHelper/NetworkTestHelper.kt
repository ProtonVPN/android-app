/*
 * Copyright (c) 2018 Proton Technologies AG
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
package com.protonvpn.testsHelper

import com.protonvpn.android.models.vpn.VpnCountry
import com.protonvpn.android.utils.CountryTools.getFullName
import com.protonvpn.base.BaseRobot

class NetworkTestHelper : BaseRobot() {

    val serverManager = ServerManagerHelper().serverManager

    val vpnCountries: List<VpnCountry>
        get() = serverManager.getVpnCountries()
    val firstNotAccessibleVpnCountry: VpnCountry
        get() = serverManager.firstNotAccessibleVpnCountry
    val exitVpnCountries: List<VpnCountry>
        get() = serverManager.getSecureCoreExitCountries()

    fun getEntryVpnCountry(exitCountry: VpnCountry?): String {
        val countryCode = serverManager.getBestScoreServer(exitCountry!!)!!.entryCountry
        return getFullName(countryCode)
    }
}
