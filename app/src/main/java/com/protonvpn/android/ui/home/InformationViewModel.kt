/*
 * Copyright (c) 2020 Proton Technologies AG
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
package com.protonvpn.android.ui.home

import androidx.lifecycle.ViewModel
import com.protonvpn.android.appconfig.AppConfig
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.partnerships.PartnershipsRepository
import com.protonvpn.android.utils.ServerManager
import com.protonvpn.android.utils.StreamingViewModelHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class InformationViewModel @Inject constructor(
    val currentUser: CurrentUser,
    override val serverManager: ServerManager,
    override val appConfig: AppConfig,
    private val partnershipsRepository: PartnershipsRepository
) : ViewModel(), StreamingViewModelHelper {

    fun isPlusUser() = currentUser.vpnUserCached()?.isUserPlusOrAbove == true

    fun getPartnerTypes() = partnershipsRepository.getPartnerTypes()

    fun getPartnersForServer(serverId: String) = partnershipsRepository.getServerPartnerships(serverId)

    fun getPartnersForCountry(countryCode: String, secureCore: Boolean) =
        serverManager.getVpnExitCountry(countryCode, secureCore)?.let {
            partnershipsRepository.getUniquePartnershipsForServers(it.serverList)
        }
}
