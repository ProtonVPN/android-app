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

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.protonvpn.android.concurrency.VpnDispatcherProvider
import com.protonvpn.android.di.WallClock
import com.protonvpn.android.logging.LogCategory
import com.protonvpn.android.logging.ProtonLogger
import com.protonvpn.android.ui.ForegroundActivityTracker
import com.protonvpn.android.utils.ReschedulableTask
import dagger.Reusable
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.withContext
import me.proton.core.network.domain.NetworkManager
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

private const val UNIQUE_WORK_NAME = "PeriodicUpdateWork"
private const val BACKOFF_DELAY_MINUTES = 10L // Only used when there is no network.

interface PeriodicUpdateWorkerScheduler {
    fun rescheduleAt(timestampMs: Long)
    fun cancel()
}

@Singleton
class PeriodicUpdateScheduler @Inject constructor(
    mainScope: CoroutineScope,
    @WallClock private val clock: () -> Long,
    private val workerScheduler: PeriodicUpdateWorkerScheduler,
    private val networkManager: NetworkManager,
    private val foregroundActivityTracker: ForegroundActivityTracker
) {
    private val updateTriggerFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val eventProcessPeriodicUpdates: Flow<Unit> = updateTriggerFlow

    private val foregroundUpdate = ReschedulableTask(mainScope, clock) { updateTriggerFlow.tryEmit(Unit) }
    private var workManagerScheduledAt = 0L

    fun scheduleAt(timestampMs: Long) {
        val isInForeground = foregroundActivityTracker.foregroundActivity != null
        if (isInForeground) {
            if (networkManager.isConnectedToNetwork()) foregroundUpdate.scheduleTo(timestampMs)
        } else {
            foregroundUpdate.cancelSchedule()
            scheduleWorkManager(timestampMs)
        }
    }

    fun cancelScheduled() {
        foregroundUpdate.cancelSchedule()
    }

    private fun scheduleWorkManager(timestampMs: Long) {
        if (workManagerScheduledAt != timestampMs) { // Avoid rescheduling WM if already scheduled at that time.
            workManagerScheduledAt = timestampMs
            workerScheduler.rescheduleAt(timestampMs)
        }
    }
}

@Reusable
class PeriodicUpdateWorkerSchedulerImpl @Inject constructor(
    @ApplicationContext private val appContext: Context
) : PeriodicUpdateWorkerScheduler {

    override fun rescheduleAt(timestampMs: Long) {
        val initialDelayMs = (timestampMs - System.currentTimeMillis()).coerceAtLeast(0)
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = OneTimeWorkRequestBuilder<PeriodicUpdateWork>()
            .setInitialDelay(initialDelayMs, TimeUnit.MILLISECONDS)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_DELAY_MINUTES, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(appContext).enqueueUniqueWork(UNIQUE_WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    override fun cancel() {
        WorkManager.getInstance(appContext).cancelUniqueWork(UNIQUE_WORK_NAME)
    }
}

@HiltWorker
class PeriodicUpdateWork @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val periodicUpdateManager: PeriodicUpdateManager,
    private val networkManager: NetworkManager,
    private val dispatcherProvider: VpnDispatcherProvider
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        // WorkManager assumes that network is available when VPN tunnel is setup while there is no underlying network
        // connection. Reschedule in this case.
        // https://issuetracker.google.com/issues/239451105
        if (!networkManager.isConnectedToNetwork())
            return Result.failure()
        withContext(dispatcherProvider.Main) {
            ProtonLogger.logCustom(LogCategory.APP_PERIODIC, "processing triggered by WorkManager")
            periodicUpdateManager.processPeriodic()
        }
        return Result.success()
    }

}
