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

import android.util.Log
import androidx.lifecycle.asFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.util.concurrent.TimeUnit

class ApiNotificationManager(
    private val wallClockMs: () -> Long,
    appConfig: AppConfig
) {

    private val testNotification = MutableStateFlow<ApiNotification?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val activeListFlow = appConfig.apiNotificationsResponseObservable.asFlow()
        .map { response -> response.notifications }
        .combine(testNotification) { notifications, testNotification ->
            if (testNotification != null) notifications + testNotification
            else notifications
        }
        .flatMapLatest { notifications ->
            flow {
                var nextUpdateDelayS: Long? = 0
                while(nextUpdateDelayS != null) {
                    delay(TimeUnit.SECONDS.toMillis(nextUpdateDelayS))
                    val nowS = TimeUnit.MILLISECONDS.toSeconds(wallClockMs())
                    emit(activeNotifications(nowS, notifications))
                    nextUpdateDelayS = nextUpdateDelayS(nowS, notifications)
                }
            }
        }

    private fun activeNotifications(nowS: Long, notifications: Array<ApiNotification>) =
        notifications.filter { nowS >= it.startTime && nowS < it.endTime }

    private fun nextUpdateDelayS(nowS: Long, notifications: Array<ApiNotification>): Long? =
        notifications.mapNotNull {
            when {
                nowS < it.startTime -> it.startTime - nowS
                nowS < it.endTime -> it.endTime - nowS
                else -> null
            }
        }.minOrNull()

    fun setTestNotificationJson(json: String) {
        try {
            testNotification.value = Json.decodeFromString<ApiNotification>(json)
        } catch(e: Throwable) {
            Log.e("ApiNotificationManager", "Error parsing JSON", e)
        }
    }
}
