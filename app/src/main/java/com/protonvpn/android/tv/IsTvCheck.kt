/*
 * Copyright (c) 2023. Proton AG
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

package com.protonvpn.android.tv

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.util.DisplayMetrics
import android.view.WindowManager
import com.protonvpn.android.BuildConfig
import com.protonvpn.android.appconfig.AppFeaturesPrefs
import com.protonvpn.android.logging.LogCategory
import com.protonvpn.android.logging.ProtonLogger
import com.protonvpn.android.utils.Constants
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.pow
import kotlin.math.sqrt

@Singleton
class IsTvCheck @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val appFeaturesPrefs: AppFeaturesPrefs
) {
    private val wasLaunchedForTv: Boolean get() = appFeaturesPrefs.wasLaunchedForTv

    operator fun invoke() = wasLaunchedForTv || isTvByAppContext()

    fun onUiLaunched(isTvIntent: Boolean) {
        ProtonLogger.logCustom(LogCategory.APP, "launching UI, is TV intent: $isTvIntent")
        appFeaturesPrefs.wasLaunchedForTv = isTvIntent
    }

    private fun isTvByAppContext(): Boolean {
        val wasDetectedTv = appFeaturesPrefs.wasTvDetected
        return if (wasDetectedTv != null) {
            wasDetectedTv
        } else {
            appContext.isTV().also {
                appFeaturesPrefs.wasTvDetected = it
            }
        }
    }

    /**
     * Consider using IsTvCheck in non-UI code to make it unit-testable.
     */
    private fun Context.isTV(): Boolean {
        val uiMode: Int = resources.configuration.uiMode
        val uiModeType = uiMode and Configuration.UI_MODE_TYPE_MASK

        return if (BuildConfig.FLAVOR_distribution == Constants.DISTRIBUTION_AMAZON || Build.MANUFACTURER == "Amazon") {
            // https://developer.amazon.com/docs/fire-tv/identify-amazon-fire-tv-devices.html
            packageManager.hasSystemFeature("amazon.hardware.fire_tv")
        } else {
            uiModeType == Configuration.UI_MODE_TYPE_TELEVISION ||
                    packageManager.hasSystemFeature(PackageManager.FEATURE_TELEVISION) ||
                    packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
                    packageManager.hasSystemFeature("amazon.hardware.fire_tv") ||
                    packageManager.hasSystemFeature(PackageManager.FEATURE_LIVE_TV) && displayDiagonalApprox() >= 10f
        }
    }

    private fun Context.displayDiagonalApprox(): Float {
        val defaultDisplay = (getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay
        val realMetrics = DisplayMetrics()
        defaultDisplay.getRealMetrics(realMetrics)

        val widthInches = realMetrics.widthPixels / realMetrics.xdpi
        val heightInches = realMetrics.heightPixels / realMetrics.ydpi
        return sqrt(widthInches.pow(2f) + heightInches.pow(2f))
    }
}
