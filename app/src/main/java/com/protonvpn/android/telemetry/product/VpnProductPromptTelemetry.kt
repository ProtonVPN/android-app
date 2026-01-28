/*
 * Copyright (c) 2026 Proton AG
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

package com.protonvpn.android.telemetry.product

import com.protonvpn.android.telemetry.CommonDimensions
import com.protonvpn.android.telemetry.TelemetryEventData
import com.protonvpn.android.telemetry.TelemetryFlowHelper
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VpnProductPromptTelemetry @Inject constructor(
    private val telemetryHelper: TelemetryFlowHelper,
    private val commonDimensions: CommonDimensions,
) {

    enum class PromptAction(val value: String) {
        Configure(value = "configure"),
        Dismiss(value = "dismiss"),
        LearnMore(value = "learn_more"),
    }

    enum class PromptContext(val value: String) {
        ConnectionPreferencesAdoption(value = "connection_preferences_tooltip"),
        ConnectionPreferencesFirstConnection(value = "connection_preferences_first_connection"),
    }

    enum class PromptType(val value: String) {
        Announcement(value = "announcement"),
        Education(value = "education"),
        FeatureDiscovery(value = "feature_discovery"),
    }

    fun trackPromptAction(type: PromptType, context: PromptContext, action: PromptAction) {
        val dimensions = buildMap {
            this[DIMENSION_PROMPT_TYPE] = type.value
            this[DIMENSION_PROMPT_CONTEXT] = context.value
            this[DIMENSION_PROMPT_ACTION] = action.value
        }

        sendEvent(eventName = EVENT_NAME_ACTION, dimensions = dimensions)
    }

    fun trackPromptDisplayed(type: PromptType, context: PromptContext) {
        val dimensions = buildMap {
            this[DIMENSION_PROMPT_TYPE] = type.value
            this[DIMENSION_PROMPT_CONTEXT] = context.value
        }

        sendEvent(eventName = EVENT_NAME_DISPLAY, dimensions = dimensions)
    }

    private fun sendEvent(eventName: String, dimensions: Map<String, String>) {
        telemetryHelper.event {
            TelemetryEventData(
                measurementGroup = MEASUREMENT_GROUP,
                eventName = eventName,
                dimensions = buildMap {
                    putAll(dimensions)

                    commonDimensions.add(this, CommonDimensions.Key.USER_TIER)
                },
            )
        }
    }

    private companion object {
        private const val MEASUREMENT_GROUP = "vpn.any.product_prompts"
        private const val EVENT_NAME_ACTION = "product_prompt_action"
        private const val EVENT_NAME_DISPLAY = "product_prompt_display"
        private const val DIMENSION_PROMPT_ACTION = "prompt_action"
        private const val DIMENSION_PROMPT_CONTEXT = "prompt_context"
        private const val DIMENSION_PROMPT_TYPE = "prompt_type"
    }

}
