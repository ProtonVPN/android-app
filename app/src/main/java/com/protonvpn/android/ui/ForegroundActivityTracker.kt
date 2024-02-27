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

package com.protonvpn.android.ui

import android.app.Activity
import android.app.Application
import com.protonvpn.android.logging.LogCategory
import com.protonvpn.android.logging.ProtonLogger
import com.protonvpn.android.utils.DefaultActivityLifecycleCallbacks
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ForegroundActivityTracker(
    mainScope: CoroutineScope,
    foregroundActivityFlow: Flow<Activity?>
) {
    @Inject constructor(
        mainScope: CoroutineScope,
        app: Application,
    ) : this(mainScope, createForegroundActivityFlow(app))

    private val dateFormat: DateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT)

    val foregroundActivityFlow = foregroundActivityFlow
        .stateIn(mainScope, SharingStarted.Eagerly, null)
    val isInForegroundFlow = foregroundActivityFlow.map {
        it != null
    }.distinctUntilChanged().stateIn(mainScope, SharingStarted.Eagerly, false)

    val foregroundActivity: Activity? get() = foregroundActivityFlow.value

    init {
        mainScope.launch {
            foregroundActivityFlow.collect { activity ->
                if (activity != null) {
                    val activityName = activity::class.java.simpleName
                    val date = dateFormat.format(Date())
                    ProtonLogger.logCustom(LogCategory.UI, "App in foreground: $activityName $date")
                } else {
                    ProtonLogger.logCustom(LogCategory.UI, "App in background")
                }
            }
        }
    }

    companion object {
        private fun createForegroundActivityFlow(app: Application) = callbackFlow {
            val lifecycleCallbacks = object : DefaultActivityLifecycleCallbacks {
                private var currentActivity: Activity? = null
                override fun onActivityStarted(activity: Activity) {
                    currentActivity = activity
                    trySend(activity)
                }

                override fun onActivityStopped(activity: Activity) {
                    if (activity == currentActivity) {
                        trySend(null)
                        currentActivity = null
                    }
                }
            }
            app.registerActivityLifecycleCallbacks(lifecycleCallbacks)
            awaitClose {
                app.unregisterActivityLifecycleCallbacks(lifecycleCallbacks)
            }
        }
    }
}
