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

package com.protonvpn.android.vpn

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.Reusable
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.withContext
import me.proton.core.network.domain.NetworkManager
import me.proton.core.util.kotlin.DispatcherProvider
import java.util.concurrent.TimeUnit
import javax.inject.Inject

interface CertRefreshScheduler {
    fun rescheduleAt(timestampMs: Long)
}

private const val UNIQUE_WORK_NAME = "CertificateRefreshWorker"

@Reusable
class CertRefreshWorkerScheduler @Inject constructor(
    @ApplicationContext private val appContext: Context
) : CertRefreshScheduler {

    override fun rescheduleAt(timestampMs: Long) {
        val initialDelayMs = (timestampMs - System.currentTimeMillis()).coerceAtLeast(0)
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = OneTimeWorkRequestBuilder<CertRefreshWorker>()
            .setInitialDelay(initialDelayMs, TimeUnit.MILLISECONDS)
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(appContext).enqueueUniqueWork(UNIQUE_WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }
}

@HiltWorker
class CertRefreshWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val dispatcherProvider: DispatcherProvider,
    private val certificateRepository: CertificateRepository,
    private val networkManager: NetworkManager
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (!networkManager.isConnectedToNetwork())
            return Result.failure()
        withContext(dispatcherProvider.Main) {
            certificateRepository.updateCertificateIfNeeded()
        }
        // Always success. CertificateRepository will schedule another refresh itself, also in case of failure.
        return Result.success()
    }
}
