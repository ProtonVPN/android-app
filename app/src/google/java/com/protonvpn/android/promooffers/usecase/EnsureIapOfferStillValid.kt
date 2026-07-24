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

package com.protonvpn.android.promooffers.usecase

import com.protonvpn.android.logging.LogCategory
import com.protonvpn.android.logging.LogLevel
import com.protonvpn.android.logging.ProtonLogger
import com.protonvpn.android.promooffers.data.ApiNotificationManager
import com.protonvpn.android.promooffers.ui.NotificationIapParams
import com.protonvpn.android.ui.planupgrade.usecase.LoadPlansConfig
import dagger.Reusable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import me.proton.core.util.kotlin.equalsNoCase
import javax.inject.Inject

@Reusable
class EnsureIapOfferStillValid @Inject constructor(
    private val mainScope: CoroutineScope,
    private val getEligibleIntroductoryOffers: GetEligibleIntroductoryOffers,
    private val apiNotificationsManager: ApiNotificationManager
) {
    suspend operator fun invoke(iapParams: NotificationIapParams) =
        with(iapParams) {
            invoke(loadPlansConfig, currency, priceCents)
        }

    private suspend operator fun invoke(
        loadPlansConfig: LoadPlansConfig,
        currency: String?,
        priceCents: Int?,
    ): Boolean {
        val valid = getEligibleIntroductoryOffers(loadPlansConfig)?.any { offer ->
            (currency == null || offer.currency equalsNoCase currency) &&
                    (priceCents == null || offer.currentPriceCents == priceCents)
        }
        val isError = valid == null
        if (valid == false) {
            mainScope.launch {
                val logInfo =
                    listOfNotNull(loadPlansConfig, currency).joinToString()
                ProtonLogger.logCustom(
                    LogLevel.DEBUG,
                    LogCategory.PROMO,
                    "User not eligible for: $logInfo"
                )
                apiNotificationsManager.updateIapIntroOffers(false)
            }
        }
        return valid == true || isError
    }
}
