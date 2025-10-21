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
import com.protonvpn.android.appconfig.ApiNotificationActions
import com.protonvpn.android.appconfig.ApiNotificationManager
import com.protonvpn.android.appconfig.ApiNotificationOfferButton
import com.protonvpn.android.appconfig.ApiNotificationTypes
import com.protonvpn.android.ui.promooffers.usecase.EnsureIapOfferStillValid
import dagger.Reusable
import kotlinx.coroutines.flow.Flow
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
    ensureIapOfferStillValid: EnsureIapOfferStillValid,
) {

    private val activeNotificationsFlow = combine(
        apiNotificationManager.activeListFlow,
        promoOffersPrefs.visitedOffersFlow
    ) { notifications, dismissedOffers ->
        notifications
            .filter {
                it.type == ApiNotificationTypes.TYPE_HOME_SCREEN_BANNER
                        && !dismissedOffers.contains(it.id)
            }.firstOrNull {

                val iapParams = with(it.offer?.panel?.button?.panel) {
                    this?.iapProductDetails?.google?.toIapParams()
                        ?: this?.button?.iapActionDetails?.toIapParams()
                }
                iapParams == null || ensureIapOfferStillValid(iapParams)
            }
    }

    operator fun invoke(isNighMode: Boolean): Flow<PromoOfferBannerState?> =
        activeNotificationsFlow.map { notification ->
            notification?.let { createPromoOfferBanner(it, isNighMode) }
        }

    // Once the light theme FF is enabled we can assume that there are always both image variants present.
    // Therefore it's ok to call invoke always for night mode in this function.
    fun hasBannerFlow(): Flow<Boolean> = invoke(isNighMode = true).map { it != null }

    private fun createPromoOfferBanner(notification: ApiNotification, isNighMode: Boolean): PromoOfferBannerState? {
        if (notification.offer?.panel?.button != null &&
            ApiNotificationActions.isSupported(notification.offer.panel.button.action) &&
            notification.offer.panel.fullScreenImage?.source?.isNotEmpty() == true
        ) {
            val fullScreenImage = notification.offer.panel.fullScreenImage
            val imageSource = fullScreenImage.source.first()
            val imageUrl = if (isNighMode) imageSource.url else imageSource.urlLight
            if (imageUrl != null) {
                return PromoOfferBannerState(
                    imageUrl,
                    fullScreenImage.alternativeText,
                    notification.offer.panel.button,
                    notification.offer.panel.isDismissible,
                    TimeUnit.SECONDS.toMillis(notification.endTime).takeIf { notification.offer.panel.showCountdown },
                    notification.id,
                    notification.reference,
                )
            }
        }
        return null
    }
}
