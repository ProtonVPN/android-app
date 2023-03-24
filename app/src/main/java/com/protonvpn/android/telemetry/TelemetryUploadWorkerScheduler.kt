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

package com.protonvpn.android.telemetry

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
import com.protonvpn.android.utils.jitterMs
import dagger.Reusable
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import me.proton.core.network.domain.NetworkManager
import java.util.concurrent.TimeUnit
import javax.inject.Inject

private val DEFAULT_UPLOAD_DELAY_MS = TimeUnit.MINUTES.toMillis(10)
private const val UNIQUE_WORK_NAME = "TelemetryUploadWorker"

@Reusable
class TelemetryUploadWorkerScheduler @Inject constructor(
    @ApplicationContext private val appContext: Context
) : TelemetryUploadScheduler {

    override fun scheduleTelemetryUpload() {
        scheduleUpload(appContext, ExistingWorkPolicy.KEEP)
    }

    companion object {
        fun scheduleUpload(
            appContext: Context,
            existingWorkPolicy: ExistingWorkPolicy,
            initialDelayMs: Long = jitterMs(DEFAULT_UPLOAD_DELAY_MS)
        ) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = OneTimeWorkRequestBuilder<TelemetryUploadWorker>()
                .setInitialDelay(initialDelayMs, TimeUnit.MILLISECONDS)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, DEFAULT_UPLOAD_DELAY_MS, TimeUnit.MILLISECONDS)
                .build()
            WorkManager.getInstance(appContext).enqueueUniqueWork(UNIQUE_WORK_NAME, existingWorkPolicy, request)
        }
    }
}

@HiltWorker
class TelemetryUploadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val telemetry: Telemetry,
    private val networkManager: NetworkManager,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (!networkManager.isConnectedToNetwork()) {
            return Result.retry()
        }
        val result = telemetry.uploadPendingEvents()
        when {
            result is Telemetry.UploadResult.Success && result.hasMoreEvents ->
                TelemetryUploadWorkerScheduler.scheduleUpload(applicationContext, ExistingWorkPolicy.REPLACE)
            result is Telemetry.UploadResult.Failure && result.retryAfter != null ->
                TelemetryUploadWorkerScheduler.scheduleUpload(
                    applicationContext,
                    ExistingWorkPolicy.REPLACE,
                    jitterMs(result.retryAfter.inWholeMilliseconds)
                )
        }
        // The result is ignored if the code above reschedules the work.
        return when (result) {
            is Telemetry.UploadResult.Success -> Result.success()
            is Telemetry.UploadResult.Failure -> if (result.isRetryable) Result.retry() else Result.failure()
        }
    }
}
