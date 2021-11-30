/*
 * Copyright (c) 2021. Proton AG
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
import android.os.PowerManager
import com.protonvpn.android.logging.LogCategory
import com.protonvpn.android.logging.ProtonLogger
import com.protonvpn.android.utils.DefaultActivityLifecycleCallbacks
import java.text.DateFormat
import java.util.Date
import java.util.Locale

class ForegroundActivityTracker(
    private val powerManager: PowerManager
) : DefaultActivityLifecycleCallbacks {

    private val dateFormat = DateFormat.getDateInstance(DateFormat.SHORT, Locale.UK)
    private var foregroundActivity: Activity? = null

    fun isInForeground() = foregroundActivity != null
    fun foregroundActivity() = foregroundActivity

    override fun onActivityResumed(activity: Activity) {
        foregroundActivity = activity
        val activityName = activity::class.java.simpleName
        val date = dateFormat.format(Date())
        ProtonLogger.logCustom(LogCategory.UI, "App in foreground: $activityName $date")
        val batteryOptimizationsIgnored = powerManager.isIgnoringBatteryOptimizations(activity.packageName)
        ProtonLogger.logCustom(LogCategory.APP, "Battery optimization ignored: $batteryOptimizationsIgnored")
    }

    override fun onActivityPaused(activity: Activity) {
        foregroundActivity = null
        ProtonLogger.logCustom(LogCategory.UI, "App in background: ${activity::class.java.simpleName}")
    }

}
