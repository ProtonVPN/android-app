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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import androidx.lifecycle.ViewModel
import com.protonvpn.android.R
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.auth.usecase.Logout
import com.protonvpn.android.models.config.UserData
import com.protonvpn.android.utils.Constants
import com.protonvpn.android.utils.Storage
import com.protonvpn.android.utils.UserPlanManager
import com.protonvpn.android.vpn.CertificateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.joda.time.DateTime
import org.joda.time.Minutes
import org.joda.time.Period
import javax.inject.Inject

@HiltViewModel
open class MainViewModel @Inject constructor(
    private val mainScope: CoroutineScope,
    private val userData: UserData,
    private val userPlanManager: UserPlanManager,
    private val certificateRepository: CertificateRepository,
    private val logoutUseCase: Logout,
    protected val currentUser: CurrentUser,
) : ViewModel(), LifecycleObserver {

    val userPlanChangeEvent = userPlanManager.planChangeFlow

    @OnLifecycleEvent(Lifecycle.Event.ON_RESUME)
    fun onResumed() {
        mainScope.launch {
            refreshVPNInfo()
            certificateRepository.checkCertificateValidity()
        }
    }

    fun isTrialUser() = userPlanManager.isTrialUser()

    fun shouldShowTrialDialog(): Boolean {
        if (isTrialUser() && !userData.wasTrialDialogRecentlyShowed()) {
            userData.setTrialDialogShownAt(DateTime())
            return true
        }
        return false
    }

    fun shouldShowExpirationDialog() = Storage.getBoolean(UserPlanManager.PREF_EXPIRATION_DIALOG_DUE)

    fun setExpirationDialogAsShown() = Storage.saveBoolean(UserPlanManager.PREF_EXPIRATION_DIALOG_DUE, false)

    private suspend fun refreshVPNInfo() {
        currentUser.vpnUser()?.let { user ->
            val ageMinutes = Minutes.minutesBetween(DateTime(user.updateTime), DateTime()).minutes
            if (ageMinutes > Constants.VPN_INFO_REFRESH_INTERVAL_MINUTES)
                userPlanManager.refreshVpnInfo()
        }
    }

    fun getTrialPeriodFlow(context: Context) = userPlanManager.getTrialPeriodFlow().map { period ->
        trialRemainingTimeString(context, period)
    }

    fun hasAccessToSecureCore() =
        currentUser.vpnUserCached()?.isUserPlusOrAbove == true

    fun logout() = mainScope.launch {
        logoutUseCase()
    }
}

fun trialRemainingTimeString(context: Context, period: Period): String {
    val resources = context.resources
    val days = resources.getQuantityString(R.plurals.counter_days, period.days, period.days)
    val hours = resources.getQuantityString(R.plurals.counter_hours, period.hours, period.hours)
    val minutes = resources.getQuantityString(R.plurals.counter_minutes, period.minutes, period.minutes)
    val seconds = resources.getQuantityString(R.plurals.counter_seconds, period.seconds, period.seconds)
    return "$days $hours $minutes $seconds"
}
