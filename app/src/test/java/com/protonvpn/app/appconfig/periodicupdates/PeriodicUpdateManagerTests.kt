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

package com.protonvpn.app.appconfig.periodicupdates

import com.protonvpn.android.appconfig.periodicupdates.MAX_JITTER_DELAY_MS
import com.protonvpn.android.appconfig.periodicupdates.MAX_JITTER_RATIO
import com.protonvpn.android.appconfig.periodicupdates.PeriodicActionResult
import com.protonvpn.android.appconfig.periodicupdates.PeriodicCallInfo
import com.protonvpn.android.appconfig.periodicupdates.PeriodicUpdateManager
import com.protonvpn.android.appconfig.periodicupdates.PeriodicUpdateManagerImpl
import com.protonvpn.android.appconfig.periodicupdates.PeriodicUpdateScheduler
import com.protonvpn.android.appconfig.periodicupdates.PeriodicUpdateSpec
import com.protonvpn.android.appconfig.periodicupdates.PeriodicUpdatesDao
import com.protonvpn.android.appconfig.periodicupdates.UpdateAction
import com.protonvpn.android.appconfig.periodicupdates.UpdateActionId
import com.protonvpn.android.appconfig.periodicupdates.registerApiCall
import com.protonvpn.android.components.AppInUseMonitor
import com.protonvpn.test.shared.MockNetworkManager
import com.protonvpn.test.shared.TestDispatcherProvider
import io.mockk.MockKAnnotations
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.RelaxedMockK
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import me.proton.core.network.domain.ApiResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

private class FakePeriodicUpdatesDao : PeriodicUpdatesDao {

    private val data = mutableListOf<PeriodicCallInfo>()
    var delayLoadingDataMs: Long = 0

    override suspend fun upsert(callInfo: PeriodicCallInfo) {
        data.removeIf { it.id == callInfo.id }
        data.add(callInfo)
    }

    override suspend fun getAll(): List<PeriodicCallInfo> {
        delay(delayLoadingDataMs)
        return data.toList()
    }
}

private const val DELAY_MS = 50L

@OptIn(ExperimentalCoroutinesApi::class)
class PeriodicUpdateManagerTests {

    private val testAction = createTestAction("action_test")

    @RelaxedMockK
    private lateinit var mockScheduler: PeriodicUpdateScheduler
    @MockK
    private lateinit var mockAppInUseMonitor: AppInUseMonitor
    @MockK
    private lateinit var mockRandom: Random

    private lateinit var testScope: TestScope
    private lateinit var networkManager: MockNetworkManager
    private lateinit var periodicUpdatesDao: FakePeriodicUpdatesDao
    private lateinit var appInUseFlow: MutableStateFlow<Boolean>

    private lateinit var periodicUpdateManager: PeriodicUpdateManager

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        val dispatcher = UnconfinedTestDispatcher()
        testScope = TestScope(dispatcher)

        networkManager = MockNetworkManager()
        periodicUpdatesDao = FakePeriodicUpdatesDao()
        appInUseFlow = MutableStateFlow(false)

        every { mockRandom.nextFloat() } returns 0f
        every { mockAppInUseMonitor.wasInUseIn(any()) } returns true
        every { mockAppInUseMonitor.isInUseFlow } returns appInUseFlow
        every { mockScheduler.eventProcessPeriodicUpdates } returns emptyFlow()

        periodicUpdateManager = PeriodicUpdateManagerImpl(
            testScope.backgroundScope,
            TestDispatcherProvider(dispatcher),
            testScope::currentTime,
            periodicUpdatesDao,
            mockScheduler,
            mockAppInUseMonitor,
            networkManager,
            mockRandom
        )
    }

    @Test
    fun `when starting action is executed and scheduled`() = testScope.runTest {
        periodicUpdateManager.registerUpdateAction(testAction, PeriodicUpdateSpec(DELAY_MS, emptySet()))
        periodicUpdateManager.start()

        assertTrue(testAction.wasExecuted)
        coVerify { mockScheduler.scheduleAt(currentTime + DELAY_MS) }
    }

    @Test
    fun `when starting action is not executed until its time comes`() = testScope.runTest {
        val previousExecutionTime = currentTime
        periodicUpdatesDao.upsert(createPeriodicCallInfo(testAction.id, previousExecutionTime))
        advanceTimeBy(DELAY_MS / 2)
        periodicUpdateManager.registerUpdateAction(testAction, PeriodicUpdateSpec(DELAY_MS, emptySet()))
        periodicUpdateManager.start()

        assertFalse(testAction.wasExecuted)
        coVerify { mockScheduler.scheduleAt(previousExecutionTime + DELAY_MS) }
    }

    @Test
    fun `when starting processPeriodic waits for startup to finish`() = testScope.runTest {
        periodicUpdatesDao.delayLoadingDataMs = 1000L
        periodicUpdateManager.registerUpdateAction(testAction, PeriodicUpdateSpec(DELAY_MS, emptySet()))

        periodicUpdateManager.start()
        val processPeriodicJob = launch { periodicUpdateManager.processPeriodic() }
        assertFalse(testAction.wasExecuted)

        processPeriodicJob.join()
        assertTrue(testAction.wasExecuted)
    }

    @Test
    fun `when conditions are not met nothing is scheduled`() = testScope.runTest {
        val condition = MutableStateFlow(false)
        periodicUpdateManager.registerUpdateAction(testAction, PeriodicUpdateSpec(DELAY_MS, setOf(condition)))
        periodicUpdateManager.start()

        assertFalse(testAction.wasExecuted)
        coVerify(exactly = 0) { mockScheduler.scheduleAt(any()) }
    }

    @Test
    fun `when conditions change execution is rescheduled`() = testScope.runTest {
        val condition = MutableStateFlow(false)
        periodicUpdateManager.registerUpdateAction(testAction, PeriodicUpdateSpec(DELAY_MS, setOf(condition)))
        periodicUpdateManager.start()

        coVerify(exactly = 0) { mockScheduler.scheduleAt(any()) }
        coVerify(exactly = 2) { mockScheduler.cancelScheduled() } // Called by start().
        condition.value = true
        coVerify(exactly = 1) { mockScheduler.scheduleAt(any()) }
        condition.value = false
        coVerify(exactly = 3) { mockScheduler.cancelScheduled() }
    }

    @Test
    fun `when conditions change ahead of time action is not executed`() = testScope.runTest {
        val condition = MutableStateFlow(false)
        periodicUpdatesDao.upsert(createPeriodicCallInfo(testAction.id, currentTime))
        periodicUpdateManager.registerUpdateAction(testAction, PeriodicUpdateSpec(DELAY_MS, setOf(condition)))
        periodicUpdateManager.start()
        advanceTimeBy(DELAY_MS - 1)

        condition.value = true
        assertFalse(testAction.wasExecuted)
    }

    @Test
    fun `when conditions change, overdue action is executed`() = testScope.runTest {
        val condition = MutableStateFlow(false)
        periodicUpdatesDao.upsert(createPeriodicCallInfo(testAction.id, currentTime))
        periodicUpdateManager.registerUpdateAction(testAction, PeriodicUpdateSpec(DELAY_MS, setOf(condition)))
        periodicUpdateManager.start()
        advanceTimeBy(DELAY_MS + 1)

        condition.value = true
        assertTrue(testAction.wasExecuted)
    }

    @Test
    fun `when multiple update specs' conditions are met, the first one is used`() = testScope.runTest {
        val condition = flowOf(true)
        periodicUpdateManager.start()
        periodicUpdateManager.registerUpdateAction(
            testAction,
            PeriodicUpdateSpec(100L, setOf(condition)),
            PeriodicUpdateSpec(10L, setOf(condition)),
        )
        coVerify { mockScheduler.scheduleAt(100L) }
    }

    @Test
    fun `when there are multiple update specs, the one with satisfied conditions is used`() = testScope.runTest {
        val conditionTrue = flowOf(true)
        val conditionFalse = flowOf(false)
        periodicUpdateManager.start()
        periodicUpdateManager.registerUpdateAction(
            testAction,
            PeriodicUpdateSpec(10L, setOf(conditionFalse)),
            PeriodicUpdateSpec(100L, setOf(conditionTrue)),
        )
        coVerify { mockScheduler.scheduleAt(100L) }
    }

    @Test
    fun `when there are multiple actions, next update is scheduled for the soonest action`() = testScope.runTest {
        val action1 = createTestAction("action1")
        val action2 = createTestAction("action2")
        periodicUpdatesDao.upsert(createPeriodicCallInfo(action1.id, 5))
        periodicUpdatesDao.upsert(createPeriodicCallInfo(action2.id, 0))

        periodicUpdateManager.registerUpdateAction(action1, PeriodicUpdateSpec(DELAY_MS, emptySet()))
        periodicUpdateManager.registerUpdateAction(action2, PeriodicUpdateSpec(DELAY_MS, emptySet()))
        periodicUpdateManager.start()

        coVerify { mockScheduler.scheduleAt(DELAY_MS) }
    }

    @Test
    fun `when action is executed, the resulting PeriodicCallInfo is stored in DB`() = testScope.runTest {
        advanceTimeBy(100)
        periodicUpdateManager.registerUpdateAction(testAction, PeriodicUpdateSpec(DELAY_MS, emptySet()))
        periodicUpdateManager.start()

        assertEquals(
            listOf(PeriodicCallInfo(testAction.id, currentTime, true, 0f, null, null)),
            periodicUpdatesDao.getAll()
        )
    }

    @Test
    fun `when action overrides next schedule time it is respected`() = testScope.runTest {
        fun actionFunc(): PeriodicActionResult<Unit> = PeriodicActionResult(Unit, true, 2 * DELAY_MS)
        val action = UpdateAction("action", ::actionFunc)

        advanceTimeBy(100) // Start at time > 0 to test that it is included in results.
        periodicUpdateManager.registerUpdateAction(action, PeriodicUpdateSpec(DELAY_MS, emptySet()))
        periodicUpdateManager.start()

        coVerify { mockScheduler.scheduleAt(currentTime + 2 * DELAY_MS) }
        assertEquals(
            PeriodicCallInfo(action.id, currentTime, true, 0f, currentTime + 2 * DELAY_MS, null),
            periodicUpdatesDao.getAll().first()
        )
    }

    @Test
    fun `when api call action response has retry-after it is respected`() = testScope.runTest {
        val retryAfter = 20.minutes
        fun actionFunc(): ApiResult<Unit> = ApiResult.Error.Http(429, "", retryAfter = retryAfter)
        val action =
            periodicUpdateManager.registerApiCall("action", ::actionFunc, PeriodicUpdateSpec(DELAY_MS, emptySet()))

        advanceTimeBy(100) // Start at time > 0 to test that it is included in results.
        periodicUpdateManager.start()

        coVerify { mockScheduler.scheduleAt(currentTime + retryAfter.inWholeMilliseconds) }
        val expectedResult =
            PeriodicCallInfo(action.id, currentTime, false, 0f, currentTime + retryAfter.inWholeMilliseconds, null)
        assertEquals(expectedResult, periodicUpdatesDao.getAll().first())
    }

    @Test
    fun `when api call action response has small retry-after a minimal value is enforced`() = testScope.runTest {
        val retryAfter = 30.seconds
        val minimalRetryDelay = 15.minutes
        fun actionFunc(): ApiResult<Unit> = ApiResult.Error.Http(429, "", retryAfter = retryAfter)
        val action =
            periodicUpdateManager.registerApiCall("action", ::actionFunc, PeriodicUpdateSpec(DELAY_MS, emptySet()))

        advanceTimeBy(100) // Start at time > 0 to test that it is included in results.
        periodicUpdateManager.start()

        coVerify { mockScheduler.scheduleAt(currentTime + minimalRetryDelay.inWholeMilliseconds) }
        val expectedResult =
            PeriodicCallInfo(action.id, currentTime, false, 0f, currentTime + minimalRetryDelay.inWholeMilliseconds, null)
        assertEquals(expectedResult, periodicUpdatesDao.getAll().first())
    }

    @Test
    fun `when api call action response has retry-after for unexpected error code it is ignored`() = testScope.runTest {
        fun actionFunc(): ApiResult<Unit> = ApiResult.Error.Http(500, "", retryAfter = (2 * DELAY_MS).milliseconds)
        val action =
            periodicUpdateManager.registerApiCall("action", ::actionFunc, PeriodicUpdateSpec(DELAY_MS, emptySet()))

        advanceTimeBy(100) // Start at time > 0 to test that it is included in results.
        periodicUpdateManager.start()

        coVerify { mockScheduler.scheduleAt(currentTime + DELAY_MS) }
        assertEquals(
            PeriodicCallInfo(action.id, currentTime, false, 0f, null, null),
            periodicUpdatesDao.getAll().first()
        )
    }

    @Test
    fun `when action is executed explicitly then periodic update doesn't run it`() = testScope.runTest {
        val longAction = createTestAction("test_action") { input ->
            delay(100)
            PeriodicActionResult(input, true)
        }
        periodicUpdatesDao.upsert(createPeriodicCallInfo(longAction.id, currentTime))
        periodicUpdateManager.registerUpdateAction(longAction, PeriodicUpdateSpec(200, emptySet()))
        periodicUpdateManager.start()

        advanceTimeBy(150)
        val explicitExecutionJob = launch { periodicUpdateManager.executeNow(longAction) }
        advanceTimeBy(1000)
        assertEquals(1, longAction.executeCount)
        explicitExecutionJob.join()
    }

    @Test
    fun `when action is executing periodically then explicit execution waits for it to finish`() = testScope.runTest {
        val longAction = createTestAction("test_action") { input ->
            delay(100)
            PeriodicActionResult(input, true)
        }
        periodicUpdateManager.registerUpdateAction(longAction, PeriodicUpdateSpec(200, emptySet()))
        periodicUpdateManager.start()

        advanceTimeBy(50)
        // The action should be still executing right now.
        assertEquals(1, longAction.executeCount)
        val explicitExecutionJob = launch { periodicUpdateManager.executeNow(longAction) }
        advanceTimeBy(25)
        assertEquals(1, longAction.executeCount)

        advanceTimeBy(26)
        assertEquals(2, longAction.executeCount)
        explicitExecutionJob.join()
    }

    @Test
    fun `random jitter is applied when scheduling actions`() = testScope.runTest {
        every { mockRandom.nextFloat() } returns 1f // This translates to max jitter which is 20% of the delay.

        periodicUpdateManager.registerUpdateAction(testAction, PeriodicUpdateSpec(DELAY_MS, emptySet()))
        periodicUpdateManager.start()

        coVerify { mockScheduler.scheduleAt((1.2f * DELAY_MS).toLong()) }
        assertEquals(
            listOf(PeriodicCallInfo(testAction.id, currentTime, true, 0.2f, null, null)),
            periodicUpdatesDao.getAll()
        )
    }

    @Test
    fun `random jitter is applied to delay override`() =
        jitterTest(1f, 15.minutes, (15 * 0.2).minutes)

    @Test
    fun `random jitter is applied to delay override with fraction`() =
        jitterTest(0.5f, 15.minutes, (15 * 0.1).minutes)

    @Test
    fun `random jitter doesn't exceed maximum`() =
        jitterTest(1f, (100 * MAX_JITTER_DELAY_MS).milliseconds, MAX_JITTER_DELAY_MS.milliseconds)

    @Test
    fun `random jitter doesn't exceed maximum with fraction`() =
        jitterTest(0.5f, (100 * MAX_JITTER_DELAY_MS).milliseconds, (MAX_JITTER_DELAY_MS * 0.5).milliseconds)

    private fun jitterTest(diceResult: Float, delayOverride: Duration, expectedJitter: Duration) = testScope.runTest {
        every { mockRandom.nextFloat() } returns diceResult // This translates to [0,1] fraction of max jitter

        fun actionFunc(): ApiResult<Unit> = ApiResult.Error.Http(429, "", retryAfter = delayOverride)
        val action =
            periodicUpdateManager.registerApiCall("action", ::actionFunc, PeriodicUpdateSpec(DELAY_MS, emptySet()))

        advanceTimeBy(100) // Start at time > 0 to test that it is included in results.
        periodicUpdateManager.start()

        val expectedTimestamp = currentTime + (delayOverride + expectedJitter).inWholeMilliseconds
        coVerify { mockScheduler.scheduleAt(expectedTimestamp) }
        assertEquals(
            listOf(PeriodicCallInfo(action.id, currentTime, false, diceResult * MAX_JITTER_RATIO, expectedTimestamp, null)),
            periodicUpdatesDao.getAll()
        )
    }

    @Test
    fun `executeNow reschedules executed action`() = testScope.runTest {
        periodicUpdateManager.registerUpdateAction(testAction, PeriodicUpdateSpec(DELAY_MS, emptySet()))
        periodicUpdateManager.start()

        coVerify { mockScheduler.scheduleAt(DELAY_MS) }
        advanceTimeBy(DELAY_MS / 2)

        periodicUpdateManager.executeNow(testAction)
        coVerify { mockScheduler.scheduleAt(currentTime + DELAY_MS) }
    }

    @Test
    fun `executeNow accepts argument and returns result`() = testScope.runTest {
        periodicUpdateManager.registerUpdateAction(testAction, PeriodicUpdateSpec(DELAY_MS, emptySet()))

        // Test action returns its input.
        val result = periodicUpdateManager.executeNow(testAction, "input")
        assertEquals("input", result)
    }

    @Test
    fun `executeNow uses default argument and returns result`() = testScope.runTest {
        periodicUpdateManager.registerUpdateAction(testAction, PeriodicUpdateSpec(DELAY_MS, emptySet()))

        // Test action returns its input.
        val result = periodicUpdateManager.executeNow(testAction)
        assertEquals("default", result)
    }

    @Test
    fun `processPeriodic() doesn't`() = testScope.runTest {
        val longAction = createTestAction("long_action") {
            delay(1000L)
            PeriodicActionResult("result", true)
        }

        periodicUpdatesDao.upsert(createPeriodicCallInfo(longAction.id, currentTime))
        periodicUpdateManager.registerUpdateAction(longAction, PeriodicUpdateSpec(DELAY_MS, emptySet()))
        periodicUpdateManager.start()

        advanceTimeBy(DELAY_MS)
        val job1 = launch { periodicUpdateManager.processPeriodic() }
        val job2 = launch { periodicUpdateManager.processPeriodic() }
        listOf(job1, job2).joinAll()

        coVerify(exactly = 1) { mockScheduler.scheduleAt(currentTime + DELAY_MS) }
        assertEquals(1, longAction.executeCount)
    }

    @Test
    fun `when registering action again its update specs are overwritten`() = testScope.runTest {
        periodicUpdateManager.registerUpdateAction(testAction, PeriodicUpdateSpec(DELAY_MS, emptySet()))
        periodicUpdateManager.start()

        periodicUpdateManager.registerUpdateAction(testAction, PeriodicUpdateSpec(2 * DELAY_MS, emptySet()))
        coVerify { mockScheduler.scheduleAt(2 * DELAY_MS) }
    }

    @Test
    fun `when app is not in use for 2 days then scheduling stops`() = testScope.runTest {
        every { mockAppInUseMonitor.wasInUseIn(any()) } returns false

        periodicUpdateManager.registerUpdateAction(testAction, PeriodicUpdateSpec(DELAY_MS, emptySet()))
        periodicUpdateManager.start()

        coVerify { mockScheduler.cancelScheduled() }
    }

    @Test
    fun `when app becomes in use again then scheduling is started`() = testScope.runTest {
        every { mockAppInUseMonitor.wasInUseIn(any()) } returns false
        appInUseFlow.value = false

        periodicUpdateManager.registerUpdateAction(testAction, PeriodicUpdateSpec(DELAY_MS, emptySet()))
        periodicUpdateManager.start()
        coVerify { mockScheduler.cancelScheduled() }
        coVerify(exactly = 0) { mockScheduler.scheduleAt(any()) }

        every { mockAppInUseMonitor.wasInUseIn(any()) } returns true
        appInUseFlow.value = true
        coVerify { mockScheduler.scheduleAt(currentTime + DELAY_MS) }
    }

    @Test
    fun `when action executes too often via delay override it is delayed for 1 hour`() = testScope.runTest {
        val runawayAction = createTestAction("runaway_action") {
            PeriodicActionResult("result", true, 10)
        }
        periodicUpdateManager.registerUpdateAction(runawayAction, PeriodicUpdateSpec(1000, emptySet()))
        periodicUpdateManager.start()

        advanceTimeBy(1000)
        repeat(10) {
            periodicUpdateManager.processPeriodic()
            advanceTimeBy(10)
        }
        assertEquals(6, runawayAction.executeCount)

        advanceTimeBy(3600 * 1000)
        periodicUpdateManager.processPeriodic()
        assertEquals(7, runawayAction.executeCount)
    }

    @Test
    fun `when action executes too often via regular interval it is delayed for 1 hour`() = testScope.runTest {
        periodicUpdateManager.registerUpdateAction(testAction, PeriodicUpdateSpec(10, emptySet()))
        periodicUpdateManager.start()

        repeat(10) {
            advanceTimeBy(10)
            periodicUpdateManager.processPeriodic()
        }
        assertEquals(6, testAction.executeCount)

        advanceTimeBy(3600 * 1000)
        periodicUpdateManager.processPeriodic()
        assertEquals(7, testAction.executeCount)
    }

    @Test
    fun `when action executes less often than 5 times per 10 minutes it is not throttled`() = testScope.runTest {
        periodicUpdateManager.registerUpdateAction(testAction, PeriodicUpdateSpec(2 * 60_000, emptySet()))
        periodicUpdateManager.start()

        repeat(999) { // Total 1000 with initial execution in start().
            advanceTimeBy(60_000)
            periodicUpdateManager.processPeriodic()
        }
        assertEquals(500, testAction.executeCount)
    }

    @Test
    fun `when action is throttled it returns to normal interval after throttle time`() = testScope.runTest {
        periodicUpdatesDao.upsert(createPeriodicCallInfo(testAction.id, currentTime, throttledTimestamp = currentTime))
        periodicUpdateManager.registerUpdateAction(testAction, PeriodicUpdateSpec(DELAY_MS, emptySet()))
        periodicUpdateManager.start()

        advanceTimeBy(3600 * 1000)
        periodicUpdateManager.processPeriodic()
        assertEquals(1, testAction.executeCount)

        advanceTimeBy(DELAY_MS)
        periodicUpdateManager.processPeriodic()
        assertEquals(2, testAction.executeCount)
    }

    @Test
    fun `when action is both throttled and has next timestamp override use the larger value`() = testScope.runTest {
        val largerThrottle = createTestAction("throttle")
        val largerOverride = createTestAction("override")
        periodicUpdatesDao.upsert(
            createPeriodicCallInfo(largerThrottle.id, 0, throttledTimestamp = 100, nextOverrideTimestamp = 50)
        )
        periodicUpdatesDao.upsert(
            createPeriodicCallInfo(largerOverride.id, 0, throttledTimestamp = 50, nextOverrideTimestamp = 100)
        )
        periodicUpdateManager.registerUpdateAction(largerThrottle, PeriodicUpdateSpec(10, emptySet()))
        periodicUpdateManager.registerUpdateAction(largerOverride, PeriodicUpdateSpec(10, emptySet()))
        periodicUpdateManager.start()

        advanceTimeBy(60)
        periodicUpdateManager.processPeriodic()
        assertFalse(largerThrottle.wasExecuted)
        assertFalse(largerOverride.wasExecuted)

        advanceTimeBy(50)
        periodicUpdateManager.processPeriodic()
        assertTrue(largerThrottle.wasExecuted)
        assertTrue(largerOverride.wasExecuted)
    }

    private fun createTestAction(
        actionId: String,
        actionFunc: suspend (String) -> PeriodicActionResult<String> = { input -> PeriodicActionResult(input, true) }
    ) =
        object : UpdateAction<String, String>(actionId, { "default" }) {
            val wasExecuted: Boolean get() = executeCount > 0
            var executeCount = 0
                private set
            var argument: String? = null
                private set

            override suspend fun executeWithDefault(): PeriodicActionResult<out String> = execute(defaultInput())

            override suspend fun execute(input: String): PeriodicActionResult<out String> {
                ++executeCount
                argument = input
                return actionFunc(input)
            }
        }

    @Suppress("LongParameterList")
    private fun createPeriodicCallInfo(
        id: UpdateActionId,
        timestamp: Long,
        wasSuccess: Boolean = true,
        jitterRatio: Float = 0f,
        nextOverrideTimestamp: Long? = null,
        throttledTimestamp: Long? = null
    ) = PeriodicCallInfo(id, timestamp, wasSuccess, jitterRatio, nextOverrideTimestamp, throttledTimestamp)
}
