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

package com.protonvpn.app.telemetry

import com.protonvpn.android.api.ProtonApiRetroFit
import com.protonvpn.android.telemetry.StatsBody
import com.protonvpn.android.telemetry.StatsEvent
import com.protonvpn.android.telemetry.TelemetryUploader
import io.mockk.MockKAnnotations
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TelemetryUploaderTests {

    @MockK
    private lateinit var mockApi: ProtonApiRetroFit

    private lateinit var telemetryUploader: TelemetryUploader

    @Before
    fun setup() {
        MockKAnnotations.init(this)

        telemetryUploader = TelemetryUploader(mockApi)
    }

    @Test
    fun `event serialization to JSON`() {
        val event = StatsEvent(
            "vpn.any.connection",
            "event",
            mapOf("some value" to 1000L),
            mapOf("dimension1" to "value1", "dimension2" to "value2")
        )
        val events = StatsBody(listOf(event))
        val jsonString = Json.encodeToString(events)
        Assert.assertEquals(
            """{"EventInfo":[{"MeasurementGroup":"vpn.any.connection","Event":"event","Values":{"some value":1000},"Dimensions":{"dimension1":"value1","dimension2":"value2"}}]}""",
            jsonString
        )
    }
}
