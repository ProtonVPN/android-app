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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
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
    internalForegroundActivityFlow: Flow<Activity?>
) {
    @Inject constructor(
        mainScope: CoroutineScope,
        app: Application,
    ) : this(mainScope, createForegroundActivityFlow(app))

    private val dateFormat: DateFormat by lazy(LazyThreadSafetyMode.NONE) {
        SimpleDateFormat("yyyy-MM-dd", Locale.ROOT)
    }

    val foregroundActivityFlow = internalForegroundActivityFlow
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
        private fun createForegroundActivityFlow(app: Application): Flow<Activity?> {
            // Don't use a callbackFlow because it attaches lifecycleCallbacks only when collection
            // starts. This may be too late even when collected with stateIn + SharingStarted.Eagerly.
            val flow = MutableStateFlow<Activity?>(null)
            val lifecycleCallbacks = object : DefaultActivityLifecycleCallbacks {
                override fun onActivityStarted(startedActivity: Activity) {
                    flow.update { startedActivity }
                }

                override fun onActivityStopped(stoppedActivity: Activity) {
                    flow.update { currentActivity ->
                        if (currentActivity == stoppedActivity) null else currentActivity
                    }
                }

                override fun onActivityResumed(activity: Activity) {
                    flow.update { activity }
                }
            }
            // It's never unregistered - there should be only one global observer created.
            app.registerActivityLifecycleCallbacks(lifecycleCallbacks)
            return flow
        }
    }
}
