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
import com.protonvpn.android.logging.LogCategory
import com.protonvpn.android.logging.ProtonLogger
import com.protonvpn.android.utils.Constants
import dagger.Reusable
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlin.math.pow
import kotlin.math.sqrt

@Reusable
class IsTvCheck @Inject constructor(
    @ApplicationContext private val appContext: Context
) {
    // TODO: use some caching, this method may be called fairly often.
    operator fun invoke(log: Boolean = false) = appContext.isTV(log)

    /**
     * Consider using IsTvCheck in non-UI code to make it unit-testable.
     */
    private fun Context.isTV(log: Boolean = false): Boolean {
        val uiMode: Int = resources.configuration.uiMode
        val uiModeType = uiMode and Configuration.UI_MODE_TYPE_MASK

        val featureTv = packageManager.hasSystemFeature(PackageManager.FEATURE_TELEVISION)
        val featureLeanback = packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
        val featureLiveTv = packageManager.hasSystemFeature(PackageManager.FEATURE_LIVE_TV)
        val featureFireTv = packageManager.hasSystemFeature("amazon.hardware.fire_tv")
        val displayDiagonalApprox = displayDiagonalApprox()

        if (log) {
            val message = "isTv: " +
                "uiModeType: $uiModeType; FEATURE_TELEVISION: $featureTv; FEATURE_LEANBACK: $featureLeanback; " +
                "FEATURE_LIVE_TV: $featureLiveTv; Amazon FireTV: $featureFireTv; diagonal: ~$displayDiagonalApprox"
            ProtonLogger.logCustom(LogCategory.APP, message)
        }

        return if (BuildConfig.FLAVOR_distribution == Constants.DISTRIBUTION_AMAZON || Build.MANUFACTURER == "Amazon") {
            // https://developer.amazon.com/docs/fire-tv/identify-amazon-fire-tv-devices.html
            featureFireTv
        } else {
            uiModeType == Configuration.UI_MODE_TYPE_TELEVISION ||
                featureTv ||
                featureLeanback ||
                featureFireTv ||
                featureLiveTv && displayDiagonalApprox >= 10f
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
