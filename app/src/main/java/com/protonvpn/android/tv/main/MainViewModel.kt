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
package com.protonvpn.android.tv.main

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.protonvpn.android.models.config.UserData
import com.protonvpn.android.utils.Constants
import com.protonvpn.android.utils.UserPlanManager
import kotlinx.coroutines.launch
import org.joda.time.DateTime
import javax.inject.Inject

open class MainViewModel @Inject constructor(
    private val userData: UserData,
    private val userPlanManager: UserPlanManager,
) : ViewModel() {

    val userPlanChangeEvent = userPlanManager.planChangeFlow

    fun isTrialUser() = userPlanManager.isTrialUser()

    fun shouldShowTrialDialog(): Boolean {
        if (isTrialUser() && !userData.wasTrialDialogRecentlyShowed()) {
            userData.setTrialDialogShownAt(DateTime())
            return true
        }
        return false
    }

    fun refreshVPNInfo() {
        if (!userData.wasVpnInfoRecentlyUpdated(Constants.VPN_INFO_REFRESH_INTERVAL_MINUTES)) {
            viewModelScope.launch {
                userPlanManager.refreshVpnInfo()
            }
        }
    }

    fun getTrialPeriodFlow(context: Context) = userPlanManager.getTrialPeriodFlow(context)
}
