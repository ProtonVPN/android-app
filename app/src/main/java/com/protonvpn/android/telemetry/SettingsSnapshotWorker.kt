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
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.protonvpn.android.components.AppInUseMonitor
import com.protonvpn.android.redesign.recents.data.DefaultConnection
import com.protonvpn.android.redesign.recents.usecases.RecentsManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

@HiltWorker
class SettingsSnapshotWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val commonDimensions: CommonDimensions,
    private val recentsManager: RecentsManager,
    private val helper: TelemetryFlowHelper,
    private val appInUseMonitor: AppInUseMonitor,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (appInUseMonitor.wasInUseIn(TimeUnit.DAYS.toDays(2))) {
            val defaultValue = recentsManager.getDefaultConnectionFlow().first().getTelemetryName()
            helper.event(sendImmediately = true) {
                val dimensions = buildMap {
                    this["default_connection_type"] = defaultValue
                    commonDimensions.add(this, CommonDimensions.Key.USER_TIER)
                }
                TelemetryEventData(
                    MEASUREMENT_GROUP,
                    EVENT_SETTINGS_SNAPSHOT, emptyMap(), dimensions
                )
            }
        }
        return Result.success()
    }
    private fun DefaultConnection.getTelemetryName(): String {
        return when (this) {
            is DefaultConnection.FastestConnection -> "fastest"
            is DefaultConnection.LastConnection -> "last_connection"
            is DefaultConnection.Recent -> "recent"
        }
    }

    companion object {
        const val MEASUREMENT_GROUP = "vpn.any.settings"
        private const val EVENT_SETTINGS_SNAPSHOT = "settings_snapshot"
    }
}
