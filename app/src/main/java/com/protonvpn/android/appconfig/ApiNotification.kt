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
    const val TYPE_OFFER = 0
}

@Serializable
class ApiNotification(
    @SerialName("NotificationID") val id: String,
    @SerialName("StartTime") val startTime: Long,
    @SerialName("EndTime") val endTime: Long,
    @SerialName("Type") val type: Int,
    @SerialName("Offer") val offer: ApiNotificationOffer? = null
)

@Serializable
class ApiNotificationOffer(
    @SerialName("Label") val label: String,
    @SerialName("URL") val url: String,
    @SerialName("Icon") val iconUrl: String,
    @SerialName("Panel") val panel: ApiNotificationOfferPanel? = null

)

@Serializable
class ApiNotificationOfferPanel(
    @SerialName("Incentive") val incentive: String,
    @SerialName("IncentivePrice") val incentivePrice: String,
    @SerialName("Pill") val pill: String,
    @SerialName("PictureURL") val pictureUrl: String,
    @SerialName("Title") val title: String,
    @SerialName("Features") val features: List<ApiNotificationOfferFeature>,
    @SerialName("FeaturesFooter") val featuresFooter: String,
    @SerialName("Button") val button: ApiNotificationOfferButton,
    @SerialName("PageFooter") val pageFooter: String
)

@Serializable
class ApiNotificationOfferFeature(
    @SerialName("IconURL") val iconUrl: String,
    @SerialName("Text") val text: String
)

@Serializable
class ApiNotificationOfferButton(
    @SerialName("Text") val text: String,
    @SerialName("URL") val url: String
)
