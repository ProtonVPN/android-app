/*
 * Copyright (c) 2022. Proton AG
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

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.settings.data.EffectiveCurrentUserSettings
import com.protonvpn.android.settings.data.LocalUserSettings
import com.protonvpn.android.telemetry.Telemetry
import com.protonvpn.android.telemetry.TelemetryCache
import com.protonvpn.android.telemetry.TelemetryEvent
import com.protonvpn.android.telemetry.TelemetryUploadScheduler
import com.protonvpn.android.telemetry.TelemetryUploader
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds

private val DISCARD_AGE = 1000.milliseconds
private const val MAX_EVENTS = 3

@OptIn(ExperimentalCoroutinesApi::class)
class TelemetryTests {

    @get:Rule
    val rule = InstantTaskExecutorRule()

    @RelaxedMockK
    private lateinit var mockCache: TelemetryCache

    @RelaxedMockK
    private lateinit var mockUploader: TelemetryUploader

    @RelaxedMockK
    private lateinit var mockScheduler: TelemetryUploadScheduler

    @MockK
    private lateinit var mockCurrentUser: CurrentUser

    private lateinit var effectiveUserSettings: EffectiveCurrentUserSettings
    private lateinit var userSettings: MutableStateFlow<LocalUserSettings>

    private lateinit var testScope: TestScope

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        testScope = TestScope(UnconfinedTestDispatcher())
        userSettings = MutableStateFlow(LocalUserSettings.Default.copy(telemetry = true))
        effectiveUserSettings = EffectiveCurrentUserSettings(testScope.backgroundScope, userSettings)

        coEvery { mockCurrentUser.isLoggedIn() } returns true

        coEvery { mockUploader.uploadEvents(any()) } returns Telemetry.UploadResult.Success(false)
    }

    @Test
    fun `when feature and setting enabled events are saved to cache`() = testScope.runTest {
        val telemetry = createNewTelemetryObject()
        telemetry.event(MEASUREMENT_GROUP, "event1", VALUES, DIMENSIONS)
        val event1 = createEvent(eventName = "event1")
        verify {
            mockCache.save(listOf(event1))
        }
        advanceTimeBy(10)
        telemetry.event(MEASUREMENT_GROUP, "event2", VALUES, DIMENSIONS)

        val event2 = createEvent(eventName = "event2")
        verify {
            mockCache.save(listOf(event1, event2))
        }
    }

    @Test
    fun `when user setting disabled then nothing is reported`() = testScope.runTest {
        userSettings.update { it.copy(telemetry = false) }
        val telemetry = createNewTelemetryObject()
        telemetry.event(MEASUREMENT_GROUP, EVENT_NAME, VALUES, DIMENSIONS)
        verify(exactly = 0) {
            mockCache.save(any())
        }
    }

    @Test
    fun `when no user is logged in events are uploaded`() = testScope.runTest {
        val telemetry = createNewTelemetryObject()
        telemetry.event(MEASUREMENT_GROUP, "event", VALUES, DIMENSIONS)
        coEvery { mockCurrentUser.isLoggedIn() } returns false

        telemetry.uploadPendingEvents()
        coVerify(exactly = 1) { mockUploader.uploadEvents(any()) }
    }

    @Test
    fun `when events are uploaded they are not uploaded again`() = testScope.runTest {
        val telemetry = createNewTelemetryObject()
        telemetry.event(MEASUREMENT_GROUP, "event1", VALUES, DIMENSIONS)
        telemetry.event(MEASUREMENT_GROUP, "event2", VALUES, DIMENSIONS)
        val event1 = createEvent(eventName = "event1")
        val event2 = createEvent(eventName = "event2")

        telemetry.uploadPendingEvents()
        telemetry.uploadPendingEvents()

        coVerify(exactly = 1) { mockUploader.uploadEvents(listOf(event1, event2)) }
        verify { mockCache.save(emptyList()) }
    }

    @Test
    fun `when events are not uploaded they are uploaded again next time`() = testScope.runTest {
        val telemetry = createNewTelemetryObject()
        telemetry.event(MEASUREMENT_GROUP, "event1", VALUES, DIMENSIONS)
        telemetry.event(MEASUREMENT_GROUP, "event2", VALUES, DIMENSIONS)
        val event1 = createEvent(eventName = "event1")
        val event2 = createEvent(eventName = "event2")

        coEvery { mockUploader.uploadEvents(any()) } returnsMany listOf(
            Telemetry.UploadResult.Failure(true, null),
            Telemetry.UploadResult.Success(false)
        )

        telemetry.uploadPendingEvents()
        verify { mockCache.save(listOf(event1, event2)) }

        telemetry.uploadPendingEvents()
        verify { mockCache.save(emptyList()) }

        coVerify(exactly = 2) { mockUploader.uploadEvents(listOf(event1, event2)) }
    }

    @Test
    fun `when upload triggers while telemetry disabled then all pending data is cleared`() = testScope.runTest {
        val telemetry = createNewTelemetryObject()
        telemetry.event(MEASUREMENT_GROUP, EVENT_NAME, VALUES, DIMENSIONS)

        userSettings.update { it.copy(telemetry = false) }
        telemetry.uploadPendingEvents()

        verify { mockCache.save(emptyList()) }
        coVerify(exactly = 0) { mockUploader.uploadEvents(any()) }
    }

    @Test
    fun `when events are reported before cache is loaded then they are at the end`() = testScope.runTest {
        advanceTimeBy(10)
        coEvery { mockCache.load(any()) } coAnswers {
            delay(10)
            listOf(createEvent(timestamp = 0, eventName = "cached"))
        }
        val telemetry = createNewTelemetryObject()
        telemetry.event(MEASUREMENT_GROUP, EVENT_NAME, VALUES, DIMENSIONS)

        advanceTimeBy(15)
        telemetry.uploadPendingEvents()

        val expectedEvents = listOf(
            createEvent(timestamp = 0, eventName = "cached"),
            createEvent(timestamp = 10)
        )
        coVerify { mockUploader.uploadEvents(expectedEvents) }
    }

    @Test
    fun `when multiple events are reported only the first one schedules upload`() =
        testScope.runTest {
            val telemetry = createNewTelemetryObject()
            telemetry.event(MEASUREMENT_GROUP, EVENT_NAME, VALUES, DIMENSIONS)
            verify(exactly = 1) { mockScheduler.scheduleTelemetryUpload() }

            telemetry.event(MEASUREMENT_GROUP, EVENT_NAME, VALUES, DIMENSIONS)
            verify(exactly = 1) { mockScheduler.scheduleTelemetryUpload() }
        }

    @Test
    fun `when event is to be sent immediately then upload is rescheduled`() = testScope.runTest {
        val telemetry = createNewTelemetryObject()
        telemetry.event(MEASUREMENT_GROUP, EVENT_NAME, VALUES, DIMENSIONS)

        verify(exactly = 1) { mockScheduler.scheduleTelemetryUpload() }

        telemetry.event(MEASUREMENT_GROUP, EVENT_NAME, VALUES, DIMENSIONS, sendImmediately = true)
        verify(exactly = 1) { mockScheduler.scheduleImmediateTelemetryUpload() }
    }

    @Test
    fun `when event exceeds the limit then the oldest event is dropped`() = testScope.runTest {
        val telemetry = createNewTelemetryObject()
        repeat(4) { index ->
            val eventNumber = index + 1
            telemetry.event(MEASUREMENT_GROUP, "event$eventNumber", VALUES, DIMENSIONS)
            advanceTimeBy(1)
        }
        telemetry.uploadPendingEvents()
        val expectedEvents = listOf(
            createEvent(timestamp = 1, "event2"),
            createEvent(timestamp = 2, "event3"),
            createEvent(timestamp = 3, "event4"),
        )
        coVerify { mockUploader.uploadEvents(expectedEvents) }
    }

    @Test
    fun `when loading events from cache exceeds the limit then the oldest events are dropped`() = testScope.runTest {
        coEvery { mockCache.load(any()) } coAnswers {
            listOf(
                createEvent(timestamp = 0, eventName = "cached1"),
                createEvent(timestamp = 1, eventName = "cached2"),
                createEvent(timestamp = 2, eventName = "cached3"),
            )
        }
        val telemetry = createNewTelemetryObject()
        advanceTimeBy(10)

        telemetry.event(MEASUREMENT_GROUP, EVENT_NAME, VALUES, DIMENSIONS)
        telemetry.uploadPendingEvents()

        val expectedEvents = listOf(
            createEvent(timestamp = 1, "cached2"),
            createEvent(timestamp = 2, "cached3"),
            createEvent(timestamp = 10, EVENT_NAME),
        )
        coVerify { mockUploader.uploadEvents(expectedEvents) }
    }

    @Test
    fun `when upload starts before cache is loaded it waits for load to finish`() = testScope.runTest {
        val events = listOf(createEvent())
        coEvery { mockCache.load(any()) } coAnswers {
            delay(10)
            events
        }
        val telemetry = createNewTelemetryObject()

        telemetry.uploadPendingEvents()
        coVerify(exactly = 1) { mockUploader.uploadEvents(events) }
    }

    @Test
    fun `when events reach discard age they are not uploaded`() = testScope.runTest {
        val telemetry = createNewTelemetryObject()
        telemetry.event(MEASUREMENT_GROUP, "old_event", VALUES, DIMENSIONS)
        advanceTimeBy(DISCARD_AGE.inWholeMilliseconds + 10)
        telemetry.event(MEASUREMENT_GROUP, "new_event", VALUES, DIMENSIONS)

        telemetry.uploadPendingEvents()
        coVerify { mockUploader.uploadEvents(listOf(createEvent(eventName = "new_event"))) }
    }

    @Test
    fun `when events are added during upload they are not lost and another upload is scheduled`() = testScope.runTest {
        val telemetry = createNewTelemetryObject()
        coEvery { mockUploader.uploadEvents(any()) } coAnswers {
            delay(10)
            Telemetry.UploadResult.Success(false)
        }

        val event1 = createEvent(eventName = "event1")
        telemetry.event(MEASUREMENT_GROUP, "event1", VALUES, DIMENSIONS)
        val uploadResultDeferred = async {
            telemetry.uploadPendingEvents()
        }

        // Report second event:
        advanceTimeBy(5)
        val event2 = createEvent(eventName = "event2")
        telemetry.event(MEASUREMENT_GROUP, "event2", VALUES, DIMENSIONS)

        val uploadResult = uploadResultDeferred.await()
        coVerify { mockUploader.uploadEvents(listOf(event1)) }
        assertEquals(Telemetry.UploadResult.Success(true), uploadResult)

        // Next upload processes the second event:
        telemetry.uploadPendingEvents()
        coVerify { mockUploader.uploadEvents(listOf(event2)) }
    }

    @Test
    fun `when a second upload is started before the previous one ended do nothing and return success`() =
        testScope.runTest {
            val telemetry = createNewTelemetryObject()
            coEvery { mockUploader.uploadEvents(any()) } coAnswers {
                delay(10)
                Telemetry.UploadResult.Success(false)
            }
            val event = createEvent(eventName = "event1")
            telemetry.event(MEASUREMENT_GROUP, "event1", VALUES, DIMENSIONS)

            launch {
                telemetry.uploadPendingEvents()
            }
            advanceTimeBy(5)
            val secondUploadResult = telemetry.uploadPendingEvents()

            assertEquals(Telemetry.UploadResult.Success(false), secondUploadResult)
            coVerify(exactly = 1) { mockUploader.uploadEvents(listOf(event)) }
        }

    private fun TestScope.createEvent(timestamp: Long = currentTime, eventName: String = EVENT_NAME) =
        TelemetryEvent(timestamp, MEASUREMENT_GROUP, eventName, VALUES, DIMENSIONS)

    private fun TestScope.createNewTelemetryObject(): Telemetry =
        Telemetry(
            backgroundScope,
            { currentTime },
            effectiveUserSettings,
            mockCache,
            mockUploader,
            mockScheduler,
            mockCurrentUser,
            DISCARD_AGE,
            MAX_EVENTS
        )

    companion object {
        private const val MEASUREMENT_GROUP = "measurement group"
        private const val EVENT_NAME = "event name"
        private val VALUES = emptyMap<String, Long>()
        private val DIMENSIONS = emptyMap<String, String>()
    }
}
