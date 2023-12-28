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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import me.proton.core.payment.domain.PaymentManager
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CachedPurchaseEnabled @Inject constructor(
    private val mainScope: CoroutineScope,
    @WallClock private val wallClock: () -> Long,
    private val paymentManager: PaymentManager,
    private val prefs: AppFeaturesPrefs
) {
    private var lastUpdateAttempt = 0L

    operator fun invoke() = prefs.purchaseEnabled

    fun refreshIfNeeded() {
        if (wallClock() - lastUpdateAttempt > MIN_REFRESH_INTERVAL)
            forceRefresh()
    }

    fun forceRefresh() {
        lastUpdateAttempt = wallClock()
        mainScope.launch {
            prefs.purchaseEnabled = paymentManager.isUpgradeAvailable(refresh=true)
        }
    }

    companion object {
        private val MIN_REFRESH_INTERVAL = TimeUnit.HOURS.toMillis(6)
    }
}
