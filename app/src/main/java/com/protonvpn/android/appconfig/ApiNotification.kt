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
package com.protonvpn.android.appconfig

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

object ApiNotificationTypes {
    //const val TYPE_TOOLBAR = 0 // No longer supported.
    const val TYPE_ONE_TIME_POPUP = 1
    const val TYPE_HOME_SCREEN_BANNER = 2
    const val TYPE_HOME_PROMINENT_BANNER = 3
    const val TYPE_NPS = 4
}

@Serializable
data class ApiNotification(
    @SerialName("NotificationID") val id: String,
    @SerialName("StartTime") val startTime: Long,
    @SerialName("EndTime") val endTime: Long,
    @SerialName("Type") val type: Int,
    @SerialName("Offer") val offer: ApiNotificationOffer? = null,
    @SerialName("Reference") val reference: String? = null,
)

@Serializable
data class ApiNotificationOffer(
    @SerialName("Label") val label: String? = null,
    @SerialName("URL") val url: String? = null,
    @SerialName("Action") val action: String = "OpenURL",
    @SerialName("Behaviors") val actionBehaviors: List<String> = emptyList(),
    @SerialName("Icon") val iconUrl: String? = null,
    @SerialName("Panel") val panel: ApiNotificationOfferPanel? = null,
    @SerialName("ProminentBanner") val prominentBanner: ApiNotificationProminentBanner? = null,
)

@Serializable
data class ApiNotificationOfferPanel(
    @SerialName("Incentive") val incentive: String? = null,
    @SerialName("IncentivePrice") val incentivePrice: String? = null,
    @SerialName("Pill") val pill: String? = null,
    @SerialName("PictureURL") val pictureUrl: String? = null,
    @SerialName("Title") val title: String? = null,
    @SerialName("Features") val features: List<ApiNotificationOfferFeature>? = null,
    @SerialName("FeaturesFooter") val featuresFooter: String? = null,
    @SerialName("Button") val button: ApiNotificationOfferButton? = null,
    @SerialName("PageFooter") val pageFooter: String? = null,
    @SerialName("FullScreenImage") val fullScreenImage: ApiNotificationOfferFullScreenImage? = null,
    @SerialName("ShowCountdown") val showCountdown: Boolean = false, // Only valid for banners.
    @SerialName("IsDismissible") val isDismissible: Boolean = true, // Only valid for banners.
)

@Serializable
data class ApiNotificationOfferFeature(
    @SerialName("IconURL") val iconUrl: String,
    @SerialName("Text") val text: String
)

@Serializable
enum class ApiNotificationProminentBannerStyle {
    @SerialName("Regular") REGULAR,
    @SerialName("Warning") WARNING,
}

@Serializable
data class ApiNotificationProminentBanner(
    @SerialName("Title") val title: String? = null,
    @SerialName("Description") val description: String? = null,
    @SerialName("ActionButton") val actionButton: ApiNotificationOfferButton? = null,
    @SerialName("DismissButtonText") val dismissButtonText: String,
    @SerialName("Style") val style: ApiNotificationProminentBannerStyle,
)

@Serializable
data class ApiNotificationOfferButton(
    @SerialName("Text") val text: String = "",
    @SerialName("URL") val url: String? = null,
    @SerialName("Action") val action: String = "OpenURL",
    @SerialName("Behaviors") val actionBehaviors: List<String> = emptyList()
)

@Serializable
data class ApiNotificationOfferFullScreenImage(
    @SerialName("Source") val source: List<ApiNotificationOfferImageSource> = emptyList(),
    @SerialName("AlternativeText") val alternativeText: String = ""
)

@Serializable
data class ApiNotificationOfferImageSource(
    @SerialName("URL") val url: String,
    @SerialName("Type") val type: String,
    @SerialName("Width") val width: Int? = null
)
