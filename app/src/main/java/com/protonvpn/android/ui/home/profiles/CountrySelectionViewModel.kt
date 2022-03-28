/*
 * Copyright (c) 2021. Proton Technologies AG
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

package com.protonvpn.android.ui.home.profiles

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import com.protonvpn.android.R
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.models.vpn.VpnCountry
import com.protonvpn.android.utils.ServerManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class CountrySelectionViewModel @Inject constructor(
    private val serverManager: ServerManager,
    private val currentUser: CurrentUser,
) : ViewModel() {

    data class CountriesGroup(
        @StringRes val label: Int,
        val countries: List<VpnCountry>,
        val isAccessible: Boolean
    ) {
        val size = countries.size
    }

    fun getCountryGroups(secureCore: Boolean): List<CountriesGroup> = when {
        secureCore -> listOf(allSecureCoreCountries())
        currentUser.vpnUserCached()?.isFreeUser == true -> freeUserCountriesGroups()
        else -> listOf(allCountries())
    }

    private fun allSecureCoreCountries(): CountriesGroup =
        CountriesGroup(R.string.listAllCountries, serverManager.getSecureCoreExitCountries(), true)

    private fun freeUserCountriesGroups(): List<CountriesGroup> {
        val (free, premium) = serverManager.getVpnCountries()
            .partition { it.hasAccessibleServer(currentUser.vpnUserCached()) }
        return listOf(
            CountriesGroup(R.string.listFreeCountries, free, true),
            CountriesGroup(R.string.listPremiumCountries_new_plans, premium, false)
        )
    }

    private fun allCountries(): CountriesGroup =
        CountriesGroup(R.string.listAllCountries, serverManager.getVpnCountries(), true)

}
