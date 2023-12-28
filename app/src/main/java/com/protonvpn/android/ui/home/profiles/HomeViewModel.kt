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
import com.protonvpn.android.appconfig.AppFeaturesPrefs
import com.protonvpn.android.appconfig.RestrictionsConfig
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.auth.usecase.Logout
import com.protonvpn.android.auth.usecase.OnSessionClosed
import com.protonvpn.android.redesign.vpn.ConnectIntent
import com.protonvpn.android.settings.data.CurrentUserLocalSettingsManager
import com.protonvpn.android.settings.data.EffectiveCurrentUserSettingsCached
import com.protonvpn.android.tv.main.MainViewModel
import com.protonvpn.android.ui.onboarding.OnboardingPreferences
import com.protonvpn.android.ui.onboarding.WhatsNewFreeController
import com.protonvpn.android.utils.ServerManager
import com.protonvpn.android.utils.Storage
import com.protonvpn.android.utils.UserPlanManager
import com.protonvpn.android.vpn.ConnectTrigger
import com.protonvpn.android.vpn.VpnStatusProviderUI
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val mainScope: CoroutineScope,
    private val effectiveUserSettings: EffectiveCurrentUserSettingsCached,
    private val userSettingManager: CurrentUserLocalSettingsManager,
    private val vpnStatusProviderUI: VpnStatusProviderUI,
    private val serverManager: ServerManager,
    userPlanManager: UserPlanManager,
    currentUser: CurrentUser,
    logoutUseCase: Logout,
    onSessionClosed: OnSessionClosed,
    private val appFeaturesPrefs: AppFeaturesPrefs,
    private val restrictionsConfig: RestrictionsConfig,
    private val whatsNewFreeController: WhatsNewFreeController,
) : MainViewModel(
    mainScope,
    userPlanManager,
    logoutUseCase,
    currentUser,
) {

    private var startOnboardingJob: Job? = null

    var showIKEv2Migration
        get() = appFeaturesPrefs.showIKEv2Migration
        set(value) { appFeaturesPrefs.showIKEv2Migration = value }

    val secureCore get() = effectiveUserSettings.value.secureCore
    val secureCoreLiveData = effectiveUserSettings.map { it.secureCore }.distinctUntilChanged().asLiveData()
    val userLiveData = currentUser.userFlow.asLiveData()
    val logoutEvent = onSessionClosed.logoutFlow.asLiveData()

    private val connectEventFlow = MutableSharedFlow<Pair<ConnectIntent, ConnectTrigger>>()
    val connectEvent = connectEventFlow.asLiveData()

    // Temporary method to help java activity collect a flow
    fun collectPlanChange(activity: AppCompatActivity, onChange: (UserPlanManager.InfoChange.PlanChange) -> Unit) {
        activity.lifecycleScope.launch {
            userPlanChangeEvent.collect {
                onChange(it)
            }
        }
    }

    fun handleUserOnboarding(block: () -> Unit) {
        startOnboardingJob?.cancel()
        startOnboardingJob = viewModelScope.launch {
            val onboardingId = Storage.getString(OnboardingPreferences.ONBOARDING_USER_ID, null)
            if (serverManager.isDownloadedAtLeastOnce() &&
                onboardingId != null &&
                currentUser.user()?.userId?.id == onboardingId
            ) {
                ensureActive()
                Storage.saveString(OnboardingPreferences.ONBOARDING_USER_ID, null)
                block()
            }
        }
    }

    fun toggleSecureCore(newIsEnabled: Boolean) {
        mainScope.launch {
            val reconnectIntent = vpnStatusProviderUI.connectionIntent
            if (vpnStatusProviderUI.isEstablishingOrConnected &&
                reconnectIntent is ConnectIntent &&
                vpnStatusProviderUI.isConnectingToSecureCore == !newIsEnabled
            ) {
                userSettingManager.updateSecureCore(newIsEnabled)

                connectEventFlow.emit(Pair(reconnectIntent, ConnectTrigger.SecureCore))
            } else {
                userSettingManager.updateSecureCore(newIsEnabled)
            }
        }
    }

    fun shouldShowWhatsNew() = whatsNewFreeController.shouldShowDialog().asLiveData()
    fun onWhatsNewShown() = whatsNewFreeController.onDialogShown()

    val isQuickConnectRestricted get() = restrictionsConfig.restrictQuickConnectSync()
}
