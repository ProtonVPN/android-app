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
package com.protonvpn.actions

import com.protonvpn.android.models.vpn.VpnCountry
import com.protonvpn.testsHelper.NetworkTestHelper

/**
 * [ServiceRobot] Contains data about servers
 */
class ServiceRobot {

    private val networkTestHelper = NetworkTestHelper()

    val firstCountryFromBackend: String
        get() = networkTestHelper.vpnCountries[0].countryName

    fun getSecureCoreEntryCountryFromBackend(country: VpnCountry?) =
        networkTestHelper.getEntryVpnCountry(country)

    val firstSecureCoreExitCountryFromBackend: VpnCountry
        get() = networkTestHelper.exitVpnCountries[0]

    val secondSecureCoreExitCountryFromBackend: VpnCountry
        get() = networkTestHelper.exitVpnCountries[1]

    val firstNotAccessibleVpnCountryFromBackend: String
        get() = networkTestHelper.firstNotAccessibleVpnCountry.countryName
}
