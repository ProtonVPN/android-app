/*
 * Copyright (c) 2026. Proton AG
 *
 *  This file is part of ProtonVPN.
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

import com.protonvpn.android.di.WallClock
import com.protonvpn.android.telemetry.CommonDimensions
import com.protonvpn.android.telemetry.TelemetryEventData
import com.protonvpn.android.telemetry.TelemetryFlowHelper
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private const val MEASUREMENT_GROUP = "vpn.any.product_prompts"

class ReviewTrackerTelemetry @Inject constructor(
    private val telemetry: TelemetryFlowHelper,
    private val commonDimensions: CommonDimensions,
    @WallClock private val clock: () -> Long,
) {
    fun reportReviewRequest(
        lastReviewTimestamp: Long,
        installTimestamp: Long,
        connectionsSinceLastReview: Int,
    ) {
        val lastTimestamp = lastReviewTimestamp.takeIf { it > 0 } ?: installTimestamp
        val days = (clock() - lastTimestamp).milliseconds.inWholeDays
        val values = mutableMapOf(
            "connections_since_last_prompt" to connectionsSinceLastReview.toLong(),
            "days_since_last_prompt" to days.toLong()
        )
        telemetry.event {
            val dimensions = buildMap {
                commonDimensions.add(
                    this,
                    CommonDimensions.Key.USER_COUNTRY,
                    CommonDimensions.Key.USER_TIER
                )
            }
            TelemetryEventData(
                MEASUREMENT_GROUP,
                "rating_booster_prompt_requested",
                values,
                dimensions
            )
        }
    }
}