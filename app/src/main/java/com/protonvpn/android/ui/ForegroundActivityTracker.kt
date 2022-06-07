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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ForegroundActivityTracker @Inject constructor(
    mainScope: CoroutineScope,
    app: Application,
) {
    private val dateFormat = DateFormat.getDateInstance(DateFormat.SHORT, Locale.UK)

    val foregroundActivityFlow = createForegroundActivityFlow(app)
        .stateIn(mainScope, SharingStarted.Eagerly, null)
    val foregroundActivity: Activity? get() = foregroundActivityFlow.value

    init {
        mainScope.launch {
            foregroundActivityFlow.collect { activity ->
                if (activity != null) {
                    val activityName = activity::class.java.simpleName
                    val date = dateFormat.format(Date())
                    ProtonLogger.logCustom(LogCategory.UI, "App in foreground: $activityName $date")
                }
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun createForegroundActivityFlow(app: Application) = callbackFlow {
        val lifecycleCallbacks = object : DefaultActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) {
                trySend(activity)
            }

            override fun onActivityPaused(activity: Activity) {
                trySend(null)
            }
        }
        app.registerActivityLifecycleCallbacks(lifecycleCallbacks)
        awaitClose {
            app.unregisterActivityLifecycleCallbacks(lifecycleCallbacks)
        }
    }
}
