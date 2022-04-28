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
package com.protonvpn.android.ui.home.profiles

import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.asLiveData
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import com.protonvpn.android.appconfig.CachedPurchaseEnabled
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.auth.usecase.Logout
import com.protonvpn.android.auth.usecase.OnSessionClosed
import com.protonvpn.android.logging.ConnError
import com.protonvpn.android.logging.ProtonLogger
import com.protonvpn.android.logging.UiReconnect
import com.protonvpn.android.models.config.UserData
import com.protonvpn.android.models.profiles.Profile
import com.protonvpn.android.models.profiles.Profile.Companion.getTempProfile
import com.protonvpn.android.models.vpn.VpnCountry
import com.protonvpn.android.tv.main.MainViewModel
import com.protonvpn.android.ui.onboarding.OnboardingPreferences
import com.protonvpn.android.utils.DebugUtils
import com.protonvpn.android.utils.ServerManager
import com.protonvpn.android.utils.Storage
import com.protonvpn.android.utils.UserPlanManager
import com.protonvpn.android.vpn.CertificateRepository
import com.protonvpn.android.vpn.VpnConnectionManager
import com.protonvpn.android.vpn.VpnStateMonitor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    mainScope: CoroutineScope,
    val userData: UserData,
    private val vpnStateMonitor: VpnStateMonitor,
    private val vpnConnectionManager: VpnConnectionManager,
    private val serverManager: ServerManager,
    userPlanManager: UserPlanManager,
    certificateRepository: CertificateRepository,
    currentUser: CurrentUser,
    logoutUseCase: Logout,
    onSessionClosed: OnSessionClosed,
    purchaseEnabled: CachedPurchaseEnabled
) : MainViewModel(mainScope, userPlanManager, certificateRepository, logoutUseCase, currentUser, purchaseEnabled) {

    // Temporary method to help java activity collect a flow
    fun collectPlanChange(activity: AppCompatActivity, onChange: (UserPlanManager.InfoChange.PlanChange) -> Unit) {
        activity.lifecycleScope.launch {
            userPlanChangeEvent.collect {
                onChange(it)
            }
        }
    }

    // Convert to a suspend method and remove the callback once HomeActivity is in Kotlin.
    fun reconnectToSameCountry(triggerAction: String, connectCallback: (newProfile: Profile) -> Unit) {
        ProtonLogger.log(UiReconnect, triggerAction)
        DebugUtils.debugAssert("Is connected") { vpnStateMonitor.isConnected }
        val connectedCountry: String = vpnStateMonitor.connectionParams!!.server.exitCountry
        val exitCountry: VpnCountry? =
            serverManager.getVpnExitCountry(connectedCountry, userData.secureCoreEnabled)
        val newServer = if (exitCountry != null) {
            serverManager.getBestScoreServer(exitCountry)
        } else {
            serverManager.getBestScoreServer(userData.secureCoreEnabled)
        }
        if (newServer != null) {
            val newProfile = getTempProfile(newServer, serverManager)
            vpnConnectionManager.disconnectWithCallback(triggerAction) { connectCallback(newProfile) }
        } else {
            val toOrFrom = if (userData.secureCoreEnabled) "to" else "from"
            ProtonLogger.log(ConnError, "Unable to find a server to connect to when switching $toOrFrom Secure Core")
            vpnConnectionManager.disconnect(triggerAction)
        }
    }

    fun handleUserOnboarding(block: () -> Unit) = viewModelScope.launch {
        val onboardingId = Storage.getString(OnboardingPreferences.ONBOARDING_USER_ID, null)
        if (serverManager.isDownloadedAtLeastOnce &&
                onboardingId != null &&
                currentUser.user()?.userId?.id == onboardingId)
            block()
    }

    val userLiveData = currentUser.userFlow.asLiveData()
    val logoutEvent = onSessionClosed.logoutFlow.asLiveData()
}
