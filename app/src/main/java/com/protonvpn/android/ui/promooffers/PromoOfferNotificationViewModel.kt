/*
 * Copyright (c) 2021. Proton Technologies AG
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

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bumptech.glide.Glide
import com.protonvpn.android.appconfig.ApiNotificationManager
import com.protonvpn.android.appconfig.ApiNotificationOfferFullScreenImage
import com.protonvpn.android.appconfig.ApiNotificationTypes
import com.protonvpn.android.utils.Storage
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PromoOfferNotificationViewModel @Inject constructor(
    private val apiNotificationManager: ApiNotificationManager,
) : ViewModel() {

    data class Notification(
        val id: String,
        val iconUrl: String,
        val pictureUrlForPreload: String?,
        val visited: Boolean
    )

    // Distinct class needed so that storage can use it as key.
    private class VisitedOffers : HashSet<String> {
        constructor() : super()
        constructor(collection: Collection<String>) : super(collection)
    }
    private val visitedOffersFlow =
        MutableStateFlow<VisitedOffers>(Storage.load(VisitedOffers::class.java, VisitedOffers()))

    val eventOpenPromoOffer = MutableSharedFlow<Notification>(extraBufferCapacity = 1)
    val offerNotification get() = combine(
        apiNotificationManager.activeListFlow,
        visitedOffersFlow
    ) { notifications, visitedOffers ->
        notifications.firstOrNull {
            it.type == ApiNotificationTypes.TYPE_OFFER && it.offer != null && it.offer.panel != null
        }?.let { notification ->
            Notification(
                notification.id,
                notification.offer!!.iconUrl,
                notification.offer.panel?.pictureUrl,
                visitedOffers.contains(notification.id)
            )
        }
    }.distinctUntilChanged()

    fun onOpenOffer(offerNotification: Notification) {
        visitedOffersFlow.value = VisitedOffers(visitedOffersFlow.value + offerNotification.id)
        Storage.save(visitedOffersFlow.value)
        eventOpenPromoOffer.tryEmit(offerNotification)
    }
}
