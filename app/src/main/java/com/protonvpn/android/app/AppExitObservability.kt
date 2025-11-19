/*
 * Copyright (c) 2025. Proton AG
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

package com.protonvpn.android.app

import android.app.ActivityManager.RunningAppProcessInfo
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import com.protonvpn.android.appconfig.usecase.LargeMetricsSampler
import com.protonvpn.android.observability.AppExitTotal
import com.protonvpn.android.utils.getAppMainProcessExitReason
import dagger.Reusable
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.proton.core.observability.domain.ObservabilityManager
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

@Reusable
class AppExitObservability @Inject constructor(
    private val mainScope: CoroutineScope,
    @ApplicationContext private val appContext: Context,
    private val observabilityManager: dagger.Lazy<ObservabilityManager>,
    private val largeMetricsSampler: LargeMetricsSampler,
) {

    fun start() {
        if (Build.VERSION.SDK_INT >= 30) {
            mainScope.launch {
                // This is called on app start, delay the actual reporting
                delay(5.seconds)
                val exitInfo = appContext.getAppMainProcessExitReason()
                if (exitInfo != null) {
                    largeMetricsSampler { multiplier ->
                        val exitReason = reasonToObservability(exitInfo.reason, exitInfo.status)
                        val importance = importanceToObservability(exitInfo.importance)
                        observabilityManager.get()
                            .enqueue(AppExitTotal(exitReason, importance, multiplier))
                    }
                }
            }
        }
    }

    private fun reasonToObservability(reason: Int, status: Int): AppExitTotal.ExitReason =
        when (reason) {
            // Add all reasons, even if they map to Other.
            // If new values are added in new Android versions they will be reported as Unsupported and should be added
            // explicitly to the list below.
            ApplicationExitInfo.REASON_ANR -> AppExitTotal.ExitReason.Anr
            ApplicationExitInfo.REASON_CRASH -> AppExitTotal.ExitReason.Crash
            ApplicationExitInfo.REASON_CRASH_NATIVE -> AppExitTotal.ExitReason.CrashNative // TODO: do we need to distinguish native crashes?
            ApplicationExitInfo.REASON_DEPENDENCY_DIED -> AppExitTotal.ExitReason.Other
            ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE-> AppExitTotal.ExitReason.ExcessiveResourceUsage
            ApplicationExitInfo.REASON_EXIT_SELF -> AppExitTotal.ExitReason.Other
            ApplicationExitInfo.REASON_FREEZER -> AppExitTotal.ExitReason.Freezer
            ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> AppExitTotal.ExitReason.InitializationFailure
            ApplicationExitInfo.REASON_LOW_MEMORY -> AppExitTotal.ExitReason.LowMemory
            ApplicationExitInfo.REASON_OTHER -> AppExitTotal.ExitReason.Other
            ApplicationExitInfo.REASON_PACKAGE_STATE_CHANGE -> AppExitTotal.ExitReason.PackageStateChange
            ApplicationExitInfo.REASON_PACKAGE_UPDATED -> AppExitTotal.ExitReason.PackageUpdated
            ApplicationExitInfo.REASON_PERMISSION_CHANGE -> AppExitTotal.ExitReason.PermissionChange
            ApplicationExitInfo.REASON_SIGNALED ->
                if (status == 9) AppExitTotal.ExitReason.Signal9
                else AppExitTotal.ExitReason.SignalOther
            ApplicationExitInfo.REASON_UNKNOWN -> AppExitTotal.ExitReason.Unknown
            ApplicationExitInfo.REASON_USER_REQUESTED -> AppExitTotal.ExitReason.UserRequest
            ApplicationExitInfo.REASON_USER_STOPPED -> AppExitTotal.ExitReason.Other

            else -> AppExitTotal.ExitReason.Unsupported // See the comment at the top of "when".
        }

    private fun importanceToObservability(importance: Int): AppExitTotal.AppImportance =
        when (importance) {
            RunningAppProcessInfo.IMPORTANCE_BACKGROUND,
            RunningAppProcessInfo.IMPORTANCE_CACHED,
            RunningAppProcessInfo.IMPORTANCE_EMPTY -> AppExitTotal.AppImportance.Cached
            RunningAppProcessInfo.IMPORTANCE_CANT_SAVE_STATE -> AppExitTotal.AppImportance.CantSaveState
            RunningAppProcessInfo.IMPORTANCE_FOREGROUND -> AppExitTotal.AppImportance.Foreground
            RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE -> AppExitTotal.AppImportance.ForegroundService
            RunningAppProcessInfo.IMPORTANCE_GONE -> AppExitTotal.AppImportance.Gone
            RunningAppProcessInfo.IMPORTANCE_PERCEPTIBLE,
            RunningAppProcessInfo.IMPORTANCE_PERCEPTIBLE_PRE_26 -> AppExitTotal.AppImportance.Perceptible
            RunningAppProcessInfo.IMPORTANCE_SERVICE -> AppExitTotal.AppImportance.Service
            RunningAppProcessInfo.IMPORTANCE_TOP_SLEEPING,
            RunningAppProcessInfo.IMPORTANCE_TOP_SLEEPING_PRE_28 -> AppExitTotal.AppImportance.TopSleeping
            RunningAppProcessInfo.IMPORTANCE_VISIBLE -> AppExitTotal.AppImportance.Visible

            else -> AppExitTotal.AppImportance.Unsupported
        }
}
