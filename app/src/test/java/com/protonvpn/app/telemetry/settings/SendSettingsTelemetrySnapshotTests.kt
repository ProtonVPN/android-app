package com.protonvpn.app.telemetry.settings

import com.protonvpn.android.components.AppInUseMonitor
import com.protonvpn.android.telemetry.TelemetryEventData
import com.protonvpn.android.telemetry.TelemetryFlowHelper
import com.protonvpn.android.telemetry.settings.SendSettingsTelemetrySnapshot
import com.protonvpn.android.telemetry.settings.GetSettingsTelemetrySnapshotDimensions
import com.protonvpn.mocks.TestTelemetryReporter
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SendSettingsTelemetrySnapshotTests {

    @MockK
    private lateinit var mockAppInUseMonitor: AppInUseMonitor

    @MockK
    private lateinit var mockGetSettingsTelemetrySnapshotDimensions: GetSettingsTelemetrySnapshotDimensions

    private lateinit var sendSettingsTelemetrySnapshot: SendSettingsTelemetrySnapshot

    private lateinit var testScope: TestScope

    private lateinit var testTelemetryReporter: TestTelemetryReporter

    private lateinit var testDispatcher: CoroutineDispatcher

    private val wasInUseDurationMs = TimeUnit.DAYS.toMillis(2)

    @Before
    fun setup() {
        MockKAnnotations.init(this)

        testDispatcher = UnconfinedTestDispatcher()

        // Required setting main dispatcher due to usage of: TelemetryFlowHelper
        Dispatchers.setMain(testDispatcher)

        testScope = TestScope(testDispatcher)

        testTelemetryReporter = TestTelemetryReporter()

        sendSettingsTelemetrySnapshot = SendSettingsTelemetrySnapshot(
            appInUseMonitor = mockAppInUseMonitor,
            helper = TelemetryFlowHelper(testScope.backgroundScope, testTelemetryReporter),
            getSettingsTelemetrySnapshotDimensions = mockGetSettingsTelemetrySnapshotDimensions
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `GIVEN usage time is not met WHEN sending telemetry THEN no events are sent`() = testScope.runTest {
        every { mockAppInUseMonitor.wasInUseIn(durationMs = wasInUseDurationMs) } returns false

        sendSettingsTelemetrySnapshot()

        assertTrue(testTelemetryReporter.collectedEvents.isEmpty())
    }

    @Test
    fun `GIVEN usage time is met WHEN sending telemetry THEN events are sent`() = testScope.runTest {
        val dimensions = emptyMap<String, String>()
        val expectedEventCount = 1
        val expectedEventData = TelemetryEventData(
            measurementGroup = "vpn.any.settings",
            eventName = "settings_snapshot",
            dimensions = dimensions,
        )
        every { mockAppInUseMonitor.wasInUseIn(durationMs = wasInUseDurationMs) } returns true
        coEvery { mockGetSettingsTelemetrySnapshotDimensions.invoke() } returns dimensions

        sendSettingsTelemetrySnapshot()

        assertEquals(expectedEventCount, testTelemetryReporter.collectedEvents.size)
        assertEquals(expectedEventData, testTelemetryReporter.collectedEvents.first())
    }

}
