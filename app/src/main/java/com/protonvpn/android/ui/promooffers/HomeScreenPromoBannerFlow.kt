/*
 * Copyright (c) 2024. Proton AG
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

package com.protonvpn.android.ui.promooffers

import com.protonvpn.android.appconfig.ApiNotification
import com.protonvpn.android.appconfig.ApiNotificationManager
import com.protonvpn.android.appconfig.ApiNotificationOfferButton
import com.protonvpn.android.appconfig.ApiNotificationTypes
import dagger.Reusable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.util.concurrent.TimeUnit
import javax.inject.Inject

data class PromoOfferBannerState(
    val imageUrl: String,
    val alternativeText: String,
    val action: ApiNotificationOfferButton,
    val isDismissible: Boolean,
    val endTimestamp: Long?,
    val notificationId: String,
    val reference: String?,
)

@Reusable
class HomeScreenPromoBannerFlow @Inject constructor(
    apiNotificationManager: ApiNotificationManager,
    promoOffersPrefs: PromoOffersPrefs,
): Flow<PromoOfferBannerState?> {

    private val activeNotificationsFlow = combine(
        apiNotificationManager.activeListFlow,
        promoOffersPrefs.visitedOffersFlow
    ) { notifications, dismissedOffers ->
        notifications.firstOrNull {
            it.type == ApiNotificationTypes.TYPE_HOME_SCREEN_BANNER && !dismissedOffers.contains(it.id)
        }
    }

    private val bannerFlow = activeNotificationsFlow
        .map { notification ->
            notification?.let { createPromoOfferBanner(it) }
        }

    private fun createPromoOfferBanner(notification: ApiNotification): PromoOfferBannerState? =
        if (notification.offer?.panel?.button?.url?.isNotEmpty() == true &&
            notification.offer.panel.fullScreenImage?.source?.isNotEmpty() == true
        ) {
            val fullScreenImage = notification.offer.panel.fullScreenImage
            val imageSource = fullScreenImage.source.first()
            PromoOfferBannerState(
                imageSource.url,
                fullScreenImage.alternativeText,
                notification.offer.panel.button,
                notification.offer.panel.isDismissible,
                TimeUnit.SECONDS.toMillis(notification.endTime).takeIf { notification.offer.panel.showCountdown },
                notification.id,
                notification.reference,
            )
        } else {
            null
        }

    override suspend fun collect(collector: FlowCollector<PromoOfferBannerState?>) {
        bannerFlow.collect(collector)
    }
}
