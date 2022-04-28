/*
 * Copyright (c) 2022 Proton AG
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

import com.protonvpn.android.di.WallClock
import com.protonvpn.android.logging.ApiLogError
import com.protonvpn.android.logging.ProtonLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import me.proton.core.featureflag.domain.FeatureFlagManager
import me.proton.core.featureflag.domain.entity.FeatureId
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class CachedPurchaseEnabled @Inject constructor(
    private val mainScope: CoroutineScope,
    @WallClock private val wallClock: () -> Long,
    private val featureFlagManager: FeatureFlagManager,
    private val prefs: AppFeaturesPrefs
) {
    private var lastUpdateAttempt = 0L

    operator fun invoke() = prefs.purchaseEnabled

    suspend fun refresh() {
        if (wallClock() - lastUpdateAttempt > MIN_REFRESH_INTERVAL) {
            lastUpdateAttempt = wallClock()
            mainScope.launch {
                try {
                    val paymentsDisabled = featureFlagManager.get(
                        userId = null,
                        featureId = FeatureId(PAYMENTS_ANDROID_DISABLED_FEATURE_FLAG),
                        refresh = true
                    )?.value
                    prefs.purchaseEnabled = paymentsDisabled == false
                } catch (exception: IOException) {
                    ProtonLogger.log(ApiLogError,
                        "failed to refresh $PAYMENTS_ANDROID_DISABLED_FEATURE_FLAG flag: ${exception.message}")
                }
            }
        }
    }

    companion object {
        private const val PAYMENTS_ANDROID_DISABLED_FEATURE_FLAG = "PaymentsAndroidDisabled"
        private val MIN_REFRESH_INTERVAL = TimeUnit.MINUTES.toMillis(3)
    }
}
