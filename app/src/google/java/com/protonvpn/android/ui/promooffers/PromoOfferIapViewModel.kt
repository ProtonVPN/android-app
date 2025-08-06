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

package com.protonvpn.android.ui.promooffers

import androidx.lifecycle.ViewModel
import com.protonvpn.android.appconfig.ApiNotificationActions
import com.protonvpn.android.appconfig.ApiNotificationIapAction
import com.protonvpn.android.appconfig.ApiNotificationManager
import com.protonvpn.android.appconfig.ApiNotificationOfferPanel
import com.protonvpn.android.appconfig.ApiNotificationTypes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import javax.inject.Inject

@HiltViewModel
class PromoOfferIapViewModel @Inject constructor(
    private val notifications: ApiNotificationManager
) : ViewModel() {

    data class OfferViewState(
        val imageUrlLight: String,
        val imageUrlDark: String,
        val imageContentDescription: String?,
        val iapData: ApiNotificationIapAction,
        val notificationReference: String?
    )

    suspend fun getOfferViewState(notificationId: String): OfferViewState? {
        // TODO: verify that the notification still matches an eligible offer
        val notification = notifications.activeListFlow
            .first()
            .find { it.id == notificationId }
            ?: return null

        return when {
            notification.type == ApiNotificationTypes.TYPE_INTERNAL_ONE_TIME_IAP_POPUP -> {
                val panel = notification.offer?.panel
                panel?.let {
                    getOfferViewState(panel, reference = notification.reference)
                }
            }

            notification.type == ApiNotificationTypes.TYPE_HOME_SCREEN_BANNER &&
                ApiNotificationActions.isInAppPurchaseFullscreen(notification.offer?.panel?.button?.action) -> {
                val panel = notification.offer?.panel?.button?.panel
                panel?.let {
                    getOfferViewState(panel, reference = notification.reference)
                }
            }

            else -> null
        }
    }

    private fun getOfferViewState(panel: ApiNotificationOfferPanel, reference: String?): OfferViewState? {
        val imageSource = panel.fullScreenImage?.source?.firstOrNull()
        val imageContentDescription = panel.fullScreenImage?.alternativeText
        val isIapAction = ApiNotificationActions.isInAppPurchaseFullscreen(panel.button?.action)
        val iapData = panel.button?.iapActionDetails
        return if (isIapAction && iapData != null && imageSource?.url != null && imageSource.urlLight != null) {
            OfferViewState(
                imageUrlDark = imageSource.url,
                imageUrlLight = imageSource.urlLight,
                imageContentDescription = imageContentDescription,
                iapData = iapData,
                notificationReference = reference,
            )
        } else {
            null
        }
    }
}
