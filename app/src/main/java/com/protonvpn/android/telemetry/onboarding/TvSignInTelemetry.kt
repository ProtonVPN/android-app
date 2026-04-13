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

package com.protonvpn.android.telemetry.onboarding

import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.telemetry.CommonDimensions
import com.protonvpn.android.telemetry.TelemetryEventData
import com.protonvpn.android.telemetry.TelemetryFlowHelper
import com.protonvpn.android.utils.getValue
import dagger.Reusable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import javax.inject.Inject
import kotlin.time.Duration.Companion.minutes

@Reusable
class TvSignInTelemetry @Inject constructor(
    private val mainScope: CoroutineScope,
    commonDimensionsLazy: dagger.Lazy<CommonDimensions>,
    telemetryFlowHelperLazy: dagger.Lazy<TelemetryFlowHelper>,
    private val currentUser: CurrentUser,
) {
    private val telemetryFlowHelper by telemetryFlowHelperLazy
    private val commonDimensions by commonDimensionsLazy

    suspend fun getCurrentUserTierValue(): String =
        commonDimensions.getValue(CommonDimensions.Key.USER_TIER)

    // Sent by TV.
    fun onTvAuthQrDisplayed() {
        telemetryFlowHelper.event(sendImmediately = true) {
            TelemetryEventData(
                measurementGroup = MEASUREMENT_GROUP,
                eventName = "tv_auth_qr_displayed",
            )
        }
    }

    // Sent by mobile scanning the QR code.
    fun onTvAuthInitiated() {
        telemetryFlowHelper.event(sendImmediately = true) {
            val dimensions = buildMap {
                put("userTierAtInitiation", getCurrentUserTierValue())
            }
            TelemetryEventData(
                measurementGroup = MEASUREMENT_GROUP,
                eventName = "tv_auth_initiated",
                dimensions = dimensions
            )
        }
    }

    // Sent by TV.
    fun onTvAuthCompleted(initialUserTier: String) {
        mainScope.launch {
            // Wait for the log in to finish before reporting this event, otherwise userTier
            // value will be incorrect. Don't wait forever, in case of errors with fetching VPN
            // data the user might sign out and retry - we don't want to send multiple events.
            withTimeout(1.minutes) { currentUser.eventVpnLogin.first() }
                ?: return@launch

            telemetryFlowHelper.event(sendImmediately = true) {
                val dimensions = buildMap {
                    commonDimensions.add(this, CommonDimensions.Key.USER_TIER)
                    put("userTierAtInitiation", initialUserTier)
                }
                TelemetryEventData(
                    measurementGroup = MEASUREMENT_GROUP,
                    eventName = "tv_auth_completed",
                    dimensions = dimensions
                )
            }
        }
    }

    companion object {
        const val MEASUREMENT_GROUP = "vpn.any.tv_signin"
    }
}