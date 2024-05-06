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

import com.protonvpn.android.telemetry.Telemetry
import com.protonvpn.android.telemetry.TelemetryEventData
import com.protonvpn.android.telemetry.TelemetryFlowHelper
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
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

    @MockK
    private lateinit var mockTelemetry: Telemetry
    private lateinit var scope: TestScope

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        scope = TestScope(StandardTestDispatcher())
        helper = TelemetryFlowHelper(scope.backgroundScope, mockTelemetry)
    }

    @Test
    fun `telemetry flow helper keeps order of events`() = scope.runTest {
        val names = mutableListOf<String>()
        every { mockTelemetry.event(any(), any(), any(), any(), any()) } answers {
            val eventName = secondArg<String>()
            names += eventName
        }
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
        assertEquals(listOf("1", "2", "3"), names)
    }
}