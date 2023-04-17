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

package com.protonvpn.tests.telemetry

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.protonvpn.android.telemetry.TelemetryCache
import com.protonvpn.android.telemetry.TelemetryEvent
import com.protonvpn.test.shared.TestDispatcherProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class TelemetryCacheTests {

    private lateinit var testScope: TestScope
    private lateinit var telemetryCache: TelemetryCache

    @Before
    fun setup() {
        val dispatcher = StandardTestDispatcher()
        testScope = TestScope(dispatcher)

        val context = ApplicationProvider.getApplicationContext<Application>()
        telemetryCache = TelemetryCache(context, testScope, TestDispatcherProvider(dispatcher))
    }

    @Test
    fun writeAndReadDataFromCache() = testScope.runTest {
        val discardTimestamp = 1000L
        val staleEvent = TelemetryEvent(0, "group", "stale event", emptyMap(), emptyMap())
        val freshEvent = TelemetryEvent(2000, "group", "fresh event", mapOf("value" to 100), mapOf("dimension" to "value"))

        telemetryCache.save(listOf(staleEvent, freshEvent))
        runCurrent()
        val loadedEvents = telemetryCache.load(discardTimestamp)

        assertEquals(listOf(freshEvent), loadedEvents)
    }

    @Test
    fun writingToCacheRemovesOldData() =  testScope.runTest {
        val event1 = TelemetryEvent(0, "group", "event1", emptyMap(), emptyMap())
        val event2 = TelemetryEvent(0, "group", "event2", emptyMap(), emptyMap())

        telemetryCache.save(listOf(event1))
        runCurrent()
        assertEquals(listOf(event1), telemetryCache.load(0))

        telemetryCache.save(listOf(event2))
        runCurrent()
        assertEquals(listOf(event2), telemetryCache.load(0))
    }
}
