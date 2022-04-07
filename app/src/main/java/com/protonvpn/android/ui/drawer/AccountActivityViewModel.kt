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
package com.protonvpn.android.ui.drawer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.auth.usecase.uiName
import com.protonvpn.android.utils.UserPlanManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AccountActivityViewModel @Inject constructor(
    private val currentUser: CurrentUser,
    private val userPlanManager: UserPlanManager
) : ViewModel() {

    data class ViewState(val planName: String?, val showCouponButton: Boolean)

    val viewState = currentUser.vpnUserFlow.map { vpnUser ->
        val canApplyCoupon = vpnUser != null && with(vpnUser) {
            isFreeUser && credit == 0 && subscribed == 0 && delinquent == 0 && !hasPaymentMethod
        }
        ViewState(vpnUser?.planDisplayName, canApplyCoupon)
    }

    init {
        viewModelScope.launch {
            // Make sure the screen displays up-to-date information.
            userPlanManager.refreshVpnInfo()
        }
    }

    suspend fun displayName() = currentUser.user()?.uiName()
}
