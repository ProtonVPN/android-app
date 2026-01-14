/*
 * Copyright (c) 2024. Proton AG
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

package com.protonvpn.mocks

import com.protonvpn.android.telemetry.TelemetryEventData
import com.protonvpn.android.telemetry.TelemetryReporter

class TestTelemetryReporter : TelemetryReporter {

    val collectedEvents = ArrayList<TelemetryEventData>()

    override fun invoke(telemetryEvent: TelemetryEventData, sendImmediately: Boolean) {
        collectedEvents.add(telemetryEvent)
    }

    fun reset() = collectedEvents.clear()
}
