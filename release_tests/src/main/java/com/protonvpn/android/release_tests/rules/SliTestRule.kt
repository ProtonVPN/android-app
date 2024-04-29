/*
 *
 *  * Copyright (c) 2023. Proton AG
 *  *
 *  * This file is part of ProtonVPN.
 *  *
 *  * ProtonVPN is free software: you can redistribute it and/or modify
 *  * it under the terms of the GNU General Public License as published by
 *  * the Free Software Foundation, either version 3 of the License, or
 *  * (at your option) any later version.
 *  *
 *  * ProtonVPN is distributed in the hope that it will be useful,
 *  * but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  * GNU General Public License for more details.
 *  *
 *  * You should have received a copy of the GNU General Public License
 *  * along with ProtonVPN.  If not, see <https://www.gnu.org/licenses/>.
 *
 */

package com.protonvpn.android.release_tests.rules

import com.protonvpn.android.release_tests.helpers.LokiClient
import com.protonvpn.android.release_tests.helpers.TestMonitoringHelper
import org.junit.runner.Description

class SliTestRule : LaunchVpnAppRule() {
    private val lokiClient: LokiClient = LokiClient()

    override fun finished(description: Description?) {
        super.finished(description)

        if (LokiClient.metrics.isEmpty()) {
            return
        }

        val metricsLabels = lokiClient.getMetricLabels()
        val metricsEntry = lokiClient.createLokiEntry(LokiClient.metrics, metricsLabels)
        lokiClient.pushLokiEntry(metricsEntry)

        val logsLabels = lokiClient.getLogsLabels()
        val logsEntry = lokiClient.createLogsEntry(lokiClient.parseLogs(), logsLabels)
        lokiClient.pushLokiEntry(logsEntry)
    }

    override fun failed(e: Throwable?, description: Description?) {
        super.failed(e, description)
        if (TestMonitoringHelper.isMonitoringStarted) {
            LokiClient.metrics["status"] = "failed"
        }
    }

    override fun succeeded(description: Description?) {
        super.succeeded(description)
        LokiClient.metrics["status"] = "passed"
    }
}