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

package com.protonvpn.android.telemetry.settings

import com.protonvpn.android.components.AppInUseMonitor
import com.protonvpn.android.telemetry.TelemetryEventData
import com.protonvpn.android.telemetry.TelemetryFlowHelper
import com.protonvpn.android.utils.Constants
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SendSettingsTelemetryHeartbeat @Inject constructor(
    private val appInUseMonitor: AppInUseMonitor,
    private val helper: TelemetryFlowHelper,
    private val getSettingsTelemetryHeartbeatDimensions: GetSettingsTelemetryHeartbeatDimensions
) {

    operator fun invoke() {
        if (!appInUseMonitor.wasInUseIn(Constants.APP_NOT_IN_USE_DELAY_MS)) return

        helper.event(sendImmediately = true) {
            TelemetryEventData(
                measurementGroup = EVENT_MEASUREMENT_GROUP,
                eventName = EVENT_NAME,
                dimensions = getSettingsTelemetryHeartbeatDimensions()
            )
        }
    }

    private companion object {
        private const val EVENT_MEASUREMENT_GROUP = "vpn.any.settings"
        private const val EVENT_NAME = "settings_heartbeat"
    }

}
