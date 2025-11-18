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
import com.protonvpn.android.telemetry.settings.SendSettingsTelemetryHeartbeat
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class SettingsHeartbeatWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val sendSettingsTelemetryHeartbeat: SendSettingsTelemetryHeartbeat,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        sendSettingsTelemetryHeartbeat()

        return Result.success()
    }

}
