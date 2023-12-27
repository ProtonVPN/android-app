/*
 * Copyright (c) 2023. Proton AG
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

package com.protonvpn.android.ui.onboarding

import com.protonvpn.android.appconfig.AppFeaturesPrefs
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.concurrency.VpnDispatcherProvider
import com.protonvpn.android.telemetry.CommonDimensions
import com.protonvpn.android.telemetry.Telemetry
import com.protonvpn.android.ui.ForegroundActivityTracker
import com.protonvpn.android.vpn.VpnStateMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import me.proton.core.auth.presentation.ui.signup.SignupActivity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OnboardingTelemetry @Inject constructor(
    private val mainScope: CoroutineScope,
    private val dispatcherProvider: VpnDispatcherProvider,
    private val telemetry: Telemetry,
    foregroundActivityTracker: ForegroundActivityTracker,
    vpnStateMonitor: VpnStateMonitor,
    private val currentUser: CurrentUser,
    private val commonDimensions: CommonDimensions,
    private val appFeaturesPrefs: AppFeaturesPrefs,
) {

    private val prefsMutex = Mutex()

    init {
        foregroundActivityTracker.foregroundActivityFlow
            .filterNotNull()
            .onEach { activity ->
                when (activity) {
                    is SignupActivity -> onSignupStart()
                    is OnboardingActivity -> onOnboardingStart()
                }
            }
            .launchIn(mainScope)
        vpnStateMonitor.newSessionEvent
            .onEach { onConnectionAttempt() }
            .launchIn(mainScope)
        onAppStart()
    }

    private fun onAppStart() = sendEvent("first_launch")

    private fun onSignupStart() = sendEvent("signup_start")

    private fun onOnboardingStart() = sendEvent("onboarding_start")

    fun onOnboardingPaymentSuccess(newPlanName: String) = sendEvent("payment_done") {
        getDimensions(newPlanName)
    }

    private fun onConnectionAttempt() = sendEvent("first_connection")

    private fun sendEvent(eventName: String, dimensions: suspend () -> Map<String, String> = { getDimensions() }) {
        mainScope.launch {
            if (hasReportedEvent(eventName)) return@launch

            storeEventReported(eventName)
            telemetry.event(MEASUREMENT_GROUP, eventName, emptyMap(), dimensions(), sendImmediately = true)
        }
    }

    private suspend fun hasReportedEvent(eventName: String) = withContext(dispatcherProvider.Io) {
        appFeaturesPrefs.reportedOnboardingEvents.contains(eventName)
    }

    private suspend fun storeEventReported(eventName: String) = withContext(dispatcherProvider.Io) {
        prefsMutex.withLock {
            val events = appFeaturesPrefs.reportedOnboardingEvents
            if (!events.contains(eventName))
                appFeaturesPrefs.reportedOnboardingEvents = events + eventName
        }
    }

    private suspend fun getDimensions(planNameOverride: String? = null): Map<String, String> = buildMap {
        val plan = planNameOverride ?: currentUser.vpnUser()?.planName
        if (plan != null) {
            put("user_plan", plan)
        }
        commonDimensions.add(this, CommonDimensions.Key.USER_COUNTRY)
    }

    companion object {
        const val MEASUREMENT_GROUP = "vpn.any.onboarding"
    }
}
