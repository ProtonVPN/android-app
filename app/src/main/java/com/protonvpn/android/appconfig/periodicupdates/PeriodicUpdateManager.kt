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

package com.protonvpn.android.appconfig.periodicupdates

import com.protonvpn.android.BuildConfig
import com.protonvpn.android.components.AppInUseMonitor
import com.protonvpn.android.di.WallClock
import com.protonvpn.android.logging.LogCategory
import com.protonvpn.android.logging.LogLevel
import com.protonvpn.android.logging.ProtonLogger
import com.protonvpn.android.utils.Constants
import io.sentry.Sentry
import io.sentry.SentryLevel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.proton.core.network.domain.ApiResult
import me.proton.core.network.domain.NetworkManager
import me.proton.core.network.domain.NetworkStatus
import me.proton.core.util.kotlin.DispatcherProvider
import org.jetbrains.annotations.VisibleForTesting
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

@VisibleForTesting const val MAX_JITTER_RATIO = .2f
@VisibleForTesting val MAX_JITTER_DELAY_MS = TimeUnit.HOURS.toMillis(1)
private val RUNAWAY_DETECT_INTERVAL_MS = TimeUnit.MINUTES.toMillis(10)
private const val RUNAWAY_EXECUTION_THRESHOLD = 5
private val RUNAWAY_ACTION_DELAY_MS = TimeUnit.HOURS.toMillis(1)
private val MAX_DELAY_OVERRIDE_MS = TimeUnit.DAYS.toMillis(7)

data class UpdateCondition(private val flow: Flow<Boolean>) {
    fun getFlow(): Flow<UpdateCondition?> = flow.map { if (it) this else null }.distinctUntilChanged()

    override fun toString(): String = flow.toString() // Used in logs.
}

data class PeriodicUpdateSpec(
    val intervalMs: Long,
    val intervalFailureMs: Long,
    private val conditions: Set<Flow<Boolean>>,
) {
    val updateConditions: Set<UpdateCondition> = conditions.mapTo(mutableSetOf()) { UpdateCondition(it) }

    constructor(intervalMs: Long, conditions: Set<Flow<Boolean>>) : this(intervalMs, intervalMs, conditions)
}

/*
 * Result returned by periodic actions.
 *
 * @param result The result of the action.
 * @param isSuccess Whether the action finished successfully.
 * @param nextCallDelayOverride Allows the task to specify delay till next call overriding the policy from Periodic
 *                              trigger. Random jitter is added to this value.
 *
 * @see PeriodicCallResult subclass for results of ApiResult type.
 */
open class PeriodicActionResult<R>(
    val result: R,
    val isSuccess: Boolean,
    val nextCallDelayOverride: Long? = null
)

/**
 * Executes actions periodically when certain conditions are met.
 * The mechanism is geared towards making API calls (it only executes actions when network is available) however the
 * interface allows executing any code.
 *
 *     // Define an action updating from backend:
 *     fun updateFunction(): ApiResult<Response> { ... }
 *
 *     // Register it to run every 10 minutes while the app is in foreground:
 *     val inForeground: Flow<Boolean> = ...
 *     val updateAction = periodicUpdateManager.registerApiCall(
 *         "action_id",
 *         ::updateAction,
 *         PeriodicUpdateSpec(10 * 60_000, setOf(inForeground))
 *     )
 *     // Execute the action explicitly:
 *     val result: ApiResult<Response> = periodicUpdateManager.executeNow(updateAction)
 *
 *     // Define and register a generic action:
 *     fun updateFunction(): PeriodicActionResult<ResultType> { ... }
 *     val updateAction = periodicUpdateManager.registerAction(
 *         "action_id",
 *         ::updateAction,
 *         PeriodicUpdateSpec(10 * 60_000, setOf(inForeground))
 *     )
 *     // Execute explicitly:
 *     val result: ResultType = periodicUpdateManager.executeNow(updateAction)
 *
 *  Actions can optionally have a single input parameter. An input value is provided explicitly to executeNow() and
 *  periodic executions require a default value.
 *
 *     fun updateWithParam(input: String): ApiResult<Response> { ... }
 *     fun getDefaultInput(): String = ...
 *     val updateAction = periodicUpdateManager.registerApiCall(
 *         "action_with_param",
 *         ::updateWithParam,
 *         ::getDefaultInput,
 *         PeriodicUpdateSpec(10 * 60_000, setOf(inForeground))
 *     )
 *     // Execute explicitly:
 *     val result: ApiResult<Response> = periodicUpdateManager.executeNow(updateAction, "custom input")
 *
 * See the documentation for public methods for more information.
 */
interface PeriodicUpdateManager {
    // To avoid unnecessary rescheduling of WorkManager (which is expensive) when application is starting call start()
    // after all app modules had a chance to register their actions.
    fun start()

    /**
     * Register a periodic update action.
     * The action will be executed according to the provided PeriodicUpdateSpec(s): when conditions are met the action
     * is executed repeatedly with the delay defined by the PeriodicUpdateSpec. If multiple PeriodicUpdateSpecs are
     * provided the first one whose conditions are met is executed (put more specific ones first and general ones at the
     * end).
     * The delay time can be overridden by the executed action, see PeriodicCallResult.nextCallDelayOverride.
     *
     * Random jitter is added to all delays.
     *
     * Registering the same action again overwrites its previous updateSpec.
     *
     * Example:
     *    // Execute an action every minute while the app is in foreground, every 60 minutes otherwise.
     *    periodicUpdateManager.registerAction(
     *        updateAction,
     *        PeriodicUpdateSpec(60_000, setOf(inForeground)),
     *        PeriodicUpdateSpec(60 * 60_000, emptySet())
     *    )
     *
     * @see registerAction
     * @see registerApiCall
     */
    fun <T, R> registerUpdateAction(action: UpdateAction<T, R>, vararg updateSpec: PeriodicUpdateSpec)

    fun unregister(action: UpdateAction<*, *>)

    /**
     * Execute an action explicitly, regardless of whether it's pending or if conditions for executing it are met.
     * Time of next periodic execution will be computed based on this call.
     *
     * Example:
     *     fun updateFoo(): PeriodicActionResult<FooResult>
     *     val updateFooAction = periodicUpdateManager.registerAction("foo", ::updateFoo, PeriodicUpdateSpec(...))
     *
     *     val result: FooResult = periodicUpdateManager.executeNow(updateFooAction)
     *
     * @return API result with the response or error.
     */
    suspend fun <T, R> executeNow(action: UpdateAction<T, R>): R

    /**
     * Same as executeNow() but for actions with an input parameter.
     *
     * Example:
     *     fun updateBar(id: String): PeriodicActionResult<BarResult> { ... }
     *     fun getDefaultId(): String = ...
     *     val updateBarAction =
     *         periodicUpdateManager.registerAction("bar", ::updateFoo, ::getDefaultId, PeriodicUpdateSpec(...))
     *
     *     val result: BarResult = periodicUpdateManager.executeNow(updateBarAction, "custom ID")
     */
    suspend fun <T, R> executeNow(action: UpdateAction<T, R>, input: T): R

    suspend fun processPeriodic()
}

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class PeriodicUpdateManagerImpl @Inject constructor(
    private val mainScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
    @WallClock private val clock: () -> Long,
    private val periodicUpdatesDao: PeriodicUpdatesDao,
    private val periodicUpdateScheduler: PeriodicUpdateScheduler,
    private val appInUseMonitor: AppInUseMonitor,
    private val networkManager: NetworkManager,
    private val random: Random
) : PeriodicUpdateManager {

    private val allConditions = MutableStateFlow<Set<UpdateCondition>>(emptySet())
    private val trueConditionsFlow: Flow<Set<UpdateCondition>> = allConditions.flatMapLatest { conditions ->
        if (conditions.isEmpty()) {
            flowOf(emptySet()) // combine() doesn't emit anything for empty input list.
        } else {
            combine(conditions.map { it.getFlow() }) { values ->
                values.filterNotNullTo(HashSet())
            }
        }
    }.onEach { newConditions ->
        trueConditions = newConditions
        ProtonLogger.logCustom(LogCategory.APP_PERIODIC, "conditions change: $newConditions")
    }.distinctUntilChanged()
    private var trueConditions: Set<UpdateCondition> = emptySet()

    data class Action(
        val updateSpecs: List<PeriodicUpdateSpec>,
        val action: UpdateAction<*, *>,
    )

    private val updateActions = HashMap<UpdateActionId, Action>()
    private val previousCalls = HashMap<UpdateActionId, PeriodicCallInfo>()

    // Keep track of tasks being in progress to avoid running them again when scheduled.
    // A task can be run more than once with by execute or a trigger like PlanChange.
    private val tasksInProgressFlow = MutableStateFlow(emptyList<UpdateActionId>())
    private val tasksInProgress: List<UpdateActionId> get() = tasksInProgressFlow.value

    private val runawayDetector = RunawayDetector(clock, RUNAWAY_DETECT_INTERVAL_MS, RUNAWAY_EXECUTION_THRESHOLD)

    private val started = CompletableDeferred<Unit>()

    override fun start() {
        mainScope.launch {
            periodicUpdatesDao.getAll().associateByTo(previousCalls) { it.id }
            started.complete(Unit)

            trueConditionsFlow
                .onEach { processPeriodic() }
                .launchIn(mainScope)
            periodicUpdateScheduler.eventProcessPeriodicUpdates
                .onEach { processPeriodic() }
                .launchIn(mainScope)
            networkManager.observe()
                .map { it != NetworkStatus.Disconnected }
                .distinctUntilChanged()
                .onEach { processPeriodic() }
                .launchIn(mainScope)
            appInUseMonitor.isInUseFlow
                .filter { isInUse -> isInUse }
                .onEach { rescheduleNext(trueConditions) }
                .launchIn(mainScope)
        }
    }

    override fun <T, R> registerUpdateAction(action: UpdateAction<T, R>, vararg updateSpec: PeriodicUpdateSpec) {
        updateActions[action.id] = Action(updateSpec.asList(), action)
        onActionsChanged()
    }

    override fun unregister(action: UpdateAction<*, *>) {
        updateActions.remove(action.id)
        onActionsChanged()
    }

    override suspend fun <T, R> executeNow(action: UpdateAction<T, R>): R =
        executeNow(action, action.defaultInput())

    override suspend fun <T, R> executeNow(action: UpdateAction<T, R>, input: T): R =
        // Explicitly execute on main thread. It's a workaround for LoginTestRule that executes login on a test thread.
        withContext(dispatcherProvider.Main) {
            // If this action is executing wait for it to finish.
            tasksInProgressFlow.first { !it.contains(action.id) }
            executeAction(action, input).also {
                rescheduleNext(trueConditions)
            }.result
        }

    override suspend fun processPeriodic() {
        started.await()
        if (networkManager.isConnectedToNetwork()) {
            executePendingPeriodic(trueConditions)
        }
        rescheduleNext(trueConditions)
    }

    private fun rescheduleNext(conditions: Set<UpdateCondition>) {
        if (tasksInProgress.isNotEmpty()) return // Reschedule only when all tasks are finished.

        val next = computeNearestNextActionTimestamp(conditions)
        if (next != null && appInUseMonitor.wasInUseIn(Constants.APP_NOT_IN_USE_DELAY_MS)) {
            periodicUpdateScheduler.scheduleAt(next)
        } else {
            periodicUpdateScheduler.cancelScheduled()
        }
    }

    private suspend fun executePendingPeriodic(currentConditions: Set<UpdateCondition>) {
        updateActions.entries
            .toList() // Work on a copy in case entries are updated during processing.
            .forEach { (id, action) ->
                val trigger = action.firstMatchingPeriodicTrigger(currentConditions)
                if (trigger != null) {
                    if (trigger.nextTimestamp(previousCalls[id]) <= clock() && !tasksInProgress.contains(id)) {
                        executeAction(action.action)
                        throttleRunawayActions(action.action)
                    }
                }
            }
    }

    private suspend fun throttleRunawayActions(lastExecutedAction: UpdateAction<*, *>) {
        val runawayActionId = runawayDetector.onActionExecuted(lastExecutedAction.id)
        if (runawayActionId != null) {
            val errorMessage = "Runaway action: $runawayActionId, throttling"
            ProtonLogger.logCustom(LogLevel.WARN, LogCategory.APP_PERIODIC, errorMessage)
            if (!BuildConfig.DEBUG) Sentry.captureMessage(errorMessage, SentryLevel.ERROR)

            val throttledTimestamp = clock() + RUNAWAY_ACTION_DELAY_MS.withJitter(randomJitterRatio())
            val callInfo = previousCalls[runawayActionId]?.copy(throttledTimestamp = throttledTimestamp)
                ?: PeriodicCallInfo(runawayActionId, clock(), true, randomJitterRatio(), null, throttledTimestamp)
            setPeriodicCallInfo(callInfo)
            runawayDetector.onActionThrottled(runawayActionId)
        }
    }

    private fun computeNearestNextActionTimestamp(currentConditions: Set<UpdateCondition>): Long? =
        updateActions.entries
            .mapNotNull { (id, action) ->
                val lastCall = previousCalls[id]
                action.firstMatchingPeriodicTrigger(currentConditions)?.nextTimestamp(lastCall)
            }.minOrNull()

    private suspend fun <R> executeAction(action: UpdateAction<*, R>): PeriodicActionResult<out R> {
        tasksInProgressFlow.value += action.id
        try {
            ProtonLogger.logCustom(LogCategory.APP_PERIODIC, "executing action ${action.id}")
            val result = action.executeWithDefault()
            updateLastCall(action, result)
            return result
        } finally {
            tasksInProgressFlow.value -= action.id
        }
    }

    private suspend fun <T, R> executeAction(action: UpdateAction<T, R>, input: T): PeriodicActionResult<out R> {
        tasksInProgressFlow.value += action.id
        try {
            ProtonLogger.logCustom(LogCategory.APP_PERIODIC, "executing action ${action.id}")
            return action.execute(input).also { result ->
                updateLastCall(action, result)
            }
        } finally {
            tasksInProgressFlow.value -= action.id
        }
    }

    private suspend fun updateLastCall(action: UpdateAction<*, *>, result: PeriodicActionResult<*>) {
        val jitterRatio = randomJitterRatio()
        val nextTimestampOverride = result.nextCallDelayOverride?.let {
            val delayOverride = minOf(it, MAX_DELAY_OVERRIDE_MS)
            clock() + delayOverride.withJitter(jitterRatio)
        }
        val callInfo = previousCalls[action.id]?.copy(
            timestamp = clock(),
            wasSuccess = result.isSuccess,
            jitterRatio = jitterRatio,
            nextTimestampOverride = nextTimestampOverride
        ) ?: PeriodicCallInfo(action.id, clock(), result.isSuccess, jitterRatio, nextTimestampOverride, null)
        setPeriodicCallInfo(callInfo)
    }

    private suspend fun setPeriodicCallInfo(periodicCallInfo: PeriodicCallInfo) {
        previousCalls[periodicCallInfo.id] = periodicCallInfo
        periodicUpdatesDao.upsert(periodicCallInfo)
    }

    private fun onActionsChanged() {
        allConditions.value = updateActions.values.flatMapTo(mutableSetOf()) { it.allConditions() }
        if (started.isCompleted) {
            mainScope.launch {
                processPeriodic()
            }
        }
    }

    private fun Action.firstMatchingPeriodicTrigger(currentConditions: Set<UpdateCondition>): PeriodicUpdateSpec? =
        updateSpecs.firstOrNull {
            currentConditions.containsAll(it.updateConditions)
        }

    private fun PeriodicUpdateSpec.nextTimestamp(lastCall: PeriodicCallInfo?): Long =
        when {
            lastCall == null -> 0L
            lastCall.throttledTimestamp != null && lastCall.throttledTimestamp > clock() ->
                if (lastCall.nextTimestampOverride != null) {
                    maxOf(lastCall.nextTimestampOverride, lastCall.throttledTimestamp)
                } else {
                    lastCall.throttledTimestamp
                }
            lastCall.nextTimestampOverride != null -> lastCall.nextTimestampOverride
            else -> with(lastCall) {
                val delayNextMs = if (wasSuccess) intervalMs else intervalFailureMs
                timestamp + delayNextMs.withJitter(jitterRatio)
            }
        }

    private fun Long.withJitter(ratio: Float) : Long {
        val scaledMaxDelayMs = (MAX_JITTER_DELAY_MS / MAX_JITTER_RATIO).toLong()
        return this + (this.coerceAtMost(scaledMaxDelayMs) * ratio).toLong()
    }

    private fun randomJitterRatio() = random.nextFloat() * MAX_JITTER_RATIO

    private fun Action.allConditions() =
        this.updateSpecs.flatMapTo(mutableSetOf()) { specs -> specs.updateConditions }
}

private class RunawayDetector(
    @WallClock private val clock: () -> Long,
    private val detectionIntervalMs: Long,
    private val maxAllowedExecutions: Int
) {

    private data class RecentExecution(val id: UpdateActionId, val timestamp: Long)

    private val recentlyExecutedActions = mutableListOf<RecentExecution>()

    fun onActionExecuted(id: UpdateActionId): UpdateActionId? {
        recentlyExecutedActions.add(0, RecentExecution(id, clock()))
        removeOld()
        val offenders = recentlyExecutedActions.groupBy { it.id }
        val worstOffenderId = offenders
            .maxByOrNull { it.value.size }
            ?.takeIf { it.value.size > maxAllowedExecutions }
            ?.key
        return worstOffenderId
    }

    fun onActionThrottled(id: UpdateActionId) {
        recentlyExecutedActions.removeAll { it.id == id }
    }

    private fun removeOld() {
        val threshold = clock() - detectionIntervalMs
        recentlyExecutedActions.removeAll { it.timestamp <= threshold }
    }
}

fun <R : Any> PeriodicUpdateManager.registerAction(
    actionId: String,
    actionFunction: suspend () -> PeriodicActionResult<out R>,
    vararg updateSpec: PeriodicUpdateSpec
): UpdateAction<Unit, R> = UpdateAction(actionId, actionFunction).also {
    registerUpdateAction(it, *updateSpec)
}

fun <T, R : Any> PeriodicUpdateManager.registerAction(
    actionId: String,
    actionFunction: suspend (T) -> PeriodicActionResult<out R>,
    defaultInput: suspend () -> T,
    vararg updateSpec: PeriodicUpdateSpec
): UpdateAction<T, R> = UpdateAction(actionId, actionFunction, defaultInput).also {
    registerUpdateAction(it, *updateSpec)
}

fun <R : Any> PeriodicUpdateManager.registerApiCall(
    actionId: String,
    actionFunction: suspend () -> ApiResult<R>,
    vararg updateSpec: PeriodicUpdateSpec
): UpdateAction<Unit, ApiResult<R>> = UpdateAction(actionId) { PeriodicApiCallResult(actionFunction()) }.also {
    registerUpdateAction(it, *updateSpec)
}

fun <T, R : Any> PeriodicUpdateManager.registerApiCall(
    actionId: String,
    actionFunction: suspend (T) -> ApiResult<R>,
    defaultInput: suspend () -> T,
    vararg updateSpec: PeriodicUpdateSpec
): UpdateAction<T, ApiResult<R>> =
    UpdateAction(actionId, { input -> PeriodicApiCallResult(actionFunction(input)) }, defaultInput).also {
        registerUpdateAction(it, *updateSpec)
    }
