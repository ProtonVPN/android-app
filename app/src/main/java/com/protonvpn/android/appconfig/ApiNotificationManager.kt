/*
 * Copyright (c) 2020 Proton Technologies AG
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

package com.protonvpn.android.appconfig

import com.protonvpn.android.utils.ReschedulableTask
import com.protonvpn.android.utils.eagerMapNotNull
import kotlinx.coroutines.CoroutineScope
import java.util.concurrent.TimeUnit

class ApiNotificationManager(
    scope: CoroutineScope,
    private val wallClockMs: () -> Long,
    private val appConfig: AppConfig
) {
    private val activityCheckTask = ReschedulableTask(scope, wallClockMs) {
        activeListObservable.value = getActiveAndSchedule()
    }

    val activeListObservable = appConfig.apiNotificationsResponseObservable.eagerMapNotNull {
        getActiveAndSchedule()
    }

    val activeList get() = activeListObservable.value!!

    private fun getActiveNotifications(timeS: Long): List<ApiNotification> =
        appConfig.apiNotifications.filter {
            timeS >= it.startTime && timeS < it.endTime
        }

    private fun scheduleNextUpdate(nowS: Long) {
        val nextUpdateDelayS = appConfig.apiNotifications.mapNotNull {
            when {
                nowS < it.startTime -> it.startTime - nowS
                nowS < it.endTime -> it.endTime - nowS
                else -> null
            }
        }.min()
        if (nextUpdateDelayS == null)
            activityCheckTask.cancelSchedule()
        else
            activityCheckTask.scheduleIn(TimeUnit.SECONDS.toMillis(nextUpdateDelayS))
    }

    // Get list of active notifications and schedule next time list needs to be updated
    private fun getActiveAndSchedule(): List<ApiNotification> {
        val nowS = wallClockMs() / 1000 // Api resolution is in seconds
        scheduleNextUpdate(nowS)
        return getActiveNotifications(nowS)
    }
}
