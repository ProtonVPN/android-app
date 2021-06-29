/*
 * Copyright (c) 2019 Proton Technologies AG
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
package com.protonvpn.android.utils

import android.app.Activity
import android.content.Context
import android.os.Build
import android.os.PowerManager
import com.protonvpn.android.BuildConfig
import com.protonvpn.android.ProtonApplication
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.asCoroutineDispatcher
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

object ProtonLogger : ProtonLoggerImpl(
    ProtonApplication.getAppContext(),
    MainScope(),
    Executors.newSingleThreadExecutor().asCoroutineDispatcher(),
    ProtonApplication.getAppContext().applicationInfo.dataDir + "/log"
) {
    private val dateFormat = DateFormat.getDateInstance(DateFormat.SHORT, Locale.UK)

    @JvmStatic
    fun logActivityResumed(activity: Activity) {
        log("App in foreground ${activity.javaClass.simpleName} " +
            "${BuildConfig.VERSION_NAME} ${dateFormat.format(Date())}")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = activity.getSystemService(Context.POWER_SERVICE) as PowerManager
            log("Battery optimization ignored: " + pm.isIgnoringBatteryOptimizations(activity.packageName))
        }
    }

    @JvmStatic
    fun logActivityPaused(activity: Activity) {
        log("App in background " + activity.javaClass.simpleName)
    }
}
