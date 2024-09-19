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
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.servers.GetStreamingServices
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class InformationViewModel @Inject constructor(
    private val currentUser: CurrentUser,
    private val streamingServices: GetStreamingServices,
) : ViewModel() {

    suspend fun isPlusUser() = currentUser.vpnUser()?.isUserPlusOrAbove == true

    fun getStreamingServices(countryCode: String) = streamingServices(countryCode)
}
