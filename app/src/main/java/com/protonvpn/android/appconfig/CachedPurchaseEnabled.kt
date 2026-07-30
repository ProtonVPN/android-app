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
import com.protonvpn.android.logging.LogCategory
import com.protonvpn.android.logging.ProtonLogger
import com.protonvpn.android.utils.UserPlanManager
import com.protonvpn.android.utils.getValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import me.proton.android.payment.purchase.usecase.GetPaymentStatus
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CachedPurchaseEnabled @Inject constructor(
    private val mainScope: CoroutineScope,
    @param:WallClock private val wallClock: () -> Long,
    getPaymentStatusLazy: dagger.Lazy<GetPaymentStatus>,
    userPlanManager: UserPlanManager,
    private val prefs: AppFeaturesPrefs,
) {
    private val getPaymentStatus by getPaymentStatusLazy

    private var lastUpdateAttempt = 0L

    init {
        userPlanManager.infoChangeFlow
            .onEach { forceRefresh() }
            .launchIn(mainScope)
    }

    operator fun invoke() = prefs.purchaseEnabled

    fun refreshIfNeeded() {
        if (wallClock() - lastUpdateAttempt > MIN_REFRESH_INTERVAL)
            forceRefresh()
    }

    fun forceRefresh() {
        lastUpdateAttempt = wallClock()
        mainScope.launch {
            getPaymentStatus()
                .onSuccess { status ->
                    prefs.purchaseEnabled = status.inApp.enabled
                }
                .onFailure { error ->
                    ProtonLogger.logCustom(LogCategory.IN_APP_PURCHASE, "Fetching payment status failed: $error")
                }
        }
    }

    companion object {
        private val MIN_REFRESH_INTERVAL = TimeUnit.HOURS.toMillis(6)
    }
}
