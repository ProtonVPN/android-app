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
