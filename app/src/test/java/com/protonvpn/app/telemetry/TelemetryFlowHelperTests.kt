/*
 * Copyright (c) 2024 Proton AG
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

@file:OptIn(ExperimentalCoroutinesApi::class)

package com.protonvpn.app.telemetry

import com.protonvpn.android.telemetry.TelemetryEventData
import com.protonvpn.android.telemetry.TelemetryFlowHelper
import com.protonvpn.mocks.TestTelemetryReporter
import io.mockk.MockKAnnotations
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals

class TelemetryFlowHelperTests {

    private lateinit var helper: TelemetryFlowHelper

    private lateinit var scope: TestScope
    private lateinit var testTelemetryReporter: TestTelemetryReporter

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        scope = TestScope(StandardTestDispatcher())
        testTelemetryReporter = TestTelemetryReporter()
        helper = TelemetryFlowHelper(scope.backgroundScope, testTelemetryReporter)
    }

    @Test
    fun `telemetry flow helper keeps order of events`() = scope.runTest {
        helper.event {
            delay(100)
            TelemetryEventData("group", "1")
        }
        helper.event {
            TelemetryEventData("group", "2")
        }
        helper.event {
            delay(50)
            TelemetryEventData("group", "3")
        }
        advanceTimeBy(200)
        assertEquals(listOf("1", "2", "3"), testTelemetryReporter.collectedEvents.map { it.eventName })
    }
}
