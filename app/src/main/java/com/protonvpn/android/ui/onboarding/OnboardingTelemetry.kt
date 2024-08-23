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
import com.protonvpn.android.telemetry.TelemetryEventData
import com.protonvpn.android.telemetry.TelemetryFlowHelper
import com.protonvpn.android.ui.ForegroundActivityTracker
import com.protonvpn.android.ui.planupgrade.BaseUpgradeDialogActivity
import com.protonvpn.android.ui.planupgrade.UpgradeOnboardingDialogActivity
import com.protonvpn.android.vpn.VpnStateMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext
import me.proton.core.auth.presentation.ui.signup.SignupActivity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OnboardingTelemetry @Inject constructor(
    mainScope: CoroutineScope,
    private val dispatcherProvider: VpnDispatcherProvider,
    foregroundActivityTracker: ForegroundActivityTracker,
    vpnStateMonitor: VpnStateMonitor,
    private val currentUser: CurrentUser,
    private val commonDimensions: CommonDimensions,
    private val appFeaturesPrefs: AppFeaturesPrefs,
    private val helper: TelemetryFlowHelper,
) {
    private enum class EventName {
        FIRST_LAUNCH, SIGNUP_START, ONBOARDING_START, PAYMENT_DONE, FIRST_CONNECTION;

        val statsName: String get() = name.lowercase()
    }

    init {
        foregroundActivityTracker.foregroundActivityFlow
            .filterNotNull()
            .onEach { activity ->
                when (activity) {
                    is SignupActivity -> onSignupStart()
                    is OnboardingActivity, is BaseUpgradeDialogActivity -> onOnboardingStart()
                }
            }
            .launchIn(mainScope)
        vpnStateMonitor.newSessionEvent
            .onEach { onConnectionAttempt() }
            .launchIn(mainScope)
    }

    fun onAppUpdate() {
        helper.runSerially {
            // This method is called just before onAppStart(), set the lock before dispatching to Io pool, otherwise
            // the coroutine from onAppStart() could grab it first.
            withContext(dispatcherProvider.Io) {
                val allEvents = EventName.entries.map { it.statsName }
                appFeaturesPrefs.reportedOnboardingEvents = allEvents
            }
        }
    }

    fun onAppStart() = sendEvent(EventName.FIRST_LAUNCH)

    private fun onSignupStart() = sendEvent(EventName.SIGNUP_START)

    private fun onOnboardingStart() = sendEvent(EventName.ONBOARDING_START)

    fun onOnboardingPaymentSuccess(newPlanName: String) = sendEvent(EventName.PAYMENT_DONE) {
        getDimensions(newPlanName)
    }

    private fun onConnectionAttempt() = sendEvent(EventName.FIRST_CONNECTION)

    private fun sendEvent(event: EventName, dimensions: suspend () -> Map<String, String> = { getDimensions() }) {
        helper.event(sendImmediately = true) {
            if (hasReportedEvent(event.statsName)) {
                null
            } else {
                storeEventReported(event.statsName)
                TelemetryEventData(MEASUREMENT_GROUP, event.statsName, emptyMap(), dimensions())
            }
        }
    }

    private suspend fun hasReportedEvent(eventName: String) = withContext(dispatcherProvider.Io) {
        appFeaturesPrefs.reportedOnboardingEvents.contains(eventName)
    }

    private suspend fun storeEventReported(eventName: String) = withContext(dispatcherProvider.Io) {
        val events = appFeaturesPrefs.reportedOnboardingEvents
        if (!events.contains(eventName))
            appFeaturesPrefs.reportedOnboardingEvents = events + eventName
    }

    private suspend fun getDimensions(planNameOverride: String? = null): Map<String, String> = buildMap {
        val plan = planNameOverride ?: currentUser.vpnUser()?.planName
        if (plan != null) {
            put("user_plan", plan)
        }
        commonDimensions.add(this, CommonDimensions.Key.USER_COUNTRY,
            CommonDimensions.Key.USER_TIER, CommonDimensions.Key.IS_CREDENTIAL_LESS_ENABLED)
    }

    companion object {
        const val MEASUREMENT_GROUP = "vpn.any.onboarding"
    }
}
