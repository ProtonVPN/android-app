/*
 * Copyright (c) 2024. Proton AG
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
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.Reusable
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject

private const val SNAPSHOT_DELAY_HRS = 24L
private const val UNIQUE_SNAPSHOT_NAME = "SettingsSnapshotWorker"

@Reusable
class SettingsSnapshotScheduler @Inject constructor(
    @ApplicationContext private val appContext: Context,
) : SnapshotScheduler {

    override fun scheduleSettingsSnapshot() {
        val workRequest =
            PeriodicWorkRequestBuilder<SettingsHeartbeatWorker>(SNAPSHOT_DELAY_HRS, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().build())
                .build()

        WorkManager.getInstance(appContext).enqueueUniquePeriodicWork(
            UNIQUE_SNAPSHOT_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            workRequest
        )
    }
}