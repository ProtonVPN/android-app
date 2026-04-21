/*
 * Copyright (c) 2026. Proton AG
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

package com.protonvpn.android.ui.planupgrade.comparison_table

import androidx.lifecycle.ViewModel
import com.protonvpn.android.servers.ServerManager2
import com.protonvpn.android.ui.home.ServerListUpdaterPrefs
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class UpsellBenefitsViewModel @Inject constructor(
    private val serverListUpdaterPrefs: ServerListUpdaterPrefs,
    private val serverManager: ServerManager2,
) : ViewModel() {

    suspend fun getFreeCountryCount(): Int = serverManager.getFreeCountries().size
    fun getAllCountryCount(): Int = serverListUpdaterPrefs.vpnCountryCount
}