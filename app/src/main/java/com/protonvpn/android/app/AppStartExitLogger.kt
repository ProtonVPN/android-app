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

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.app.ApplicationStartInfo
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import com.protonvpn.android.logging.LogCategory
import com.protonvpn.android.logging.ProtonLogger
import com.protonvpn.android.logging.toImportanceLog
import dagger.Reusable
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.proton.core.util.kotlin.DispatcherProvider
import javax.inject.Inject

@Reusable
class AppStartExitLogger @Inject constructor(
    private val mainScope: CoroutineScope,
    @ApplicationContext private val appContext: Context,
    private val dispatcherProvider: DispatcherProvider,
) {
    fun log() {
        if (Build.VERSION.SDK_INT < 30) return

        mainScope.launch {
            withContext(dispatcherProvider.Io) {
                val activityManager = appContext.getSystemService(ActivityManager::class.java)

                if (Build.VERSION.SDK_INT >= 35) {
                    val startInfoText = try {
                        activityManager.getHistoricalProcessStartReasons(5)
                             .firstOrNull { it.processName == appContext.packageName }
                             ?.toLogString()
                    } catch (_: NullPointerException) {
                        // Realme, OPPO and OnePlus like to crash when processing the binder transaction results.
                        "error obtaining start info"
                    }
                    if (startInfoText != null) {
                        ProtonLogger.logCustom(LogCategory.APP, "Start cause: ${startInfoText}")
                    }
                }

                val exitInfo = activityManager.getHistoricalProcessExitReasons(appContext.packageName, 0, 5)
                    .firstOrNull { it.processName == appContext.packageName }
                if (exitInfo != null) {
                    ProtonLogger.logCustom(LogCategory.APP, "Last exit: ${exitInfo.toLogString()}")
                }
            }
        }
    }

    @RequiresApi(35)
    private fun ApplicationStartInfo.toLogString(): String {
        val reasonLabel = when(reason) {
            ApplicationStartInfo.START_REASON_ALARM -> "alarm"
            ApplicationStartInfo.START_REASON_BACKUP -> "backup"
            ApplicationStartInfo.START_REASON_BOOT_COMPLETE -> "boot complete"
            ApplicationStartInfo.START_REASON_BROADCAST -> "broadcast"
            ApplicationStartInfo.START_REASON_CONTENT_PROVIDER -> "content provider"
            ApplicationStartInfo.START_REASON_JOB -> "job scheduler"
            ApplicationStartInfo.START_REASON_LAUNCHER -> "launcher"
            ApplicationStartInfo.START_REASON_LAUNCHER_RECENTS -> "launcher recents"
            ApplicationStartInfo.START_REASON_OTHER -> "other"
            ApplicationStartInfo.START_REASON_PUSH -> "push message"
            ApplicationStartInfo.START_REASON_SERVICE -> "service"
            ApplicationStartInfo.START_REASON_START_ACTIVITY -> "start activity"

            else -> "unsupported"
        }
        return "reason: $reasonLabel"
    }

    @RequiresApi(30)
    private fun ApplicationExitInfo.toLogString(): String {
        val reasonLabel = when (reason) {
            ApplicationExitInfo.REASON_ANR -> "anr"
            ApplicationExitInfo.REASON_CRASH -> "crash"
            ApplicationExitInfo.REASON_CRASH_NATIVE -> "crash native"
            ApplicationExitInfo.REASON_DEPENDENCY_DIED -> "dependency died"
            ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE-> "excessive resource usage"
            ApplicationExitInfo.REASON_EXIT_SELF -> "exit self"
            ApplicationExitInfo.REASON_FREEZER -> "freezer"
            ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "initialization failure"
            ApplicationExitInfo.REASON_LOW_MEMORY -> "low memory"
            ApplicationExitInfo.REASON_OTHER -> "other"
            ApplicationExitInfo.REASON_PACKAGE_STATE_CHANGE -> "package state change"
            ApplicationExitInfo.REASON_PACKAGE_UPDATED -> "package updated"
            ApplicationExitInfo.REASON_PERMISSION_CHANGE -> "permission change"
            ApplicationExitInfo.REASON_SIGNALED -> "signal $status"
            ApplicationExitInfo.REASON_UNKNOWN -> "unknown"
            ApplicationExitInfo.REASON_USER_REQUESTED -> "user requested"
            ApplicationExitInfo.REASON_USER_STOPPED -> "user stopped"

            else -> "unsupported"
        }
        return "$description; reason: $reasonLabel; importance: ${importance.toImportanceLog()}; " +
            "time: ${ProtonLogger.formatTime(timestamp)}"
    }
}
