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

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.distinctUntilChanged
import com.protonvpn.android.appconfig.ApiNotificationManager
import com.protonvpn.android.appconfig.ApiNotificationTypes
import com.protonvpn.android.utils.Storage
import com.protonvpn.android.utils.mapMany
import kotlinx.coroutines.flow.MutableSharedFlow
import javax.inject.Inject

class PromoOfferNotificationViewModel @Inject constructor(
    private val apiNotificationManager: ApiNotificationManager,
) : ViewModel() {

    val eventOpenPromoOffer = MutableSharedFlow<String>(extraBufferCapacity = 1)

    data class Notification(
        val id: String,
        val iconUrl: String,
        val visited: Boolean
    )

    // Distinct class needed so that storage can use it as key.
    private class VisitedOffers : HashSet<String>()
    private var visitedOffersObservable =
        MutableLiveData(Storage.load(VisitedOffers::class.java, VisitedOffers()))

    val offerNotification get() = mapMany(
        apiNotificationManager.activeListObservable,
        visitedOffersObservable
    ) { notifications, visitedOffers ->
        notifications.firstOrNull {
            it.type == ApiNotificationTypes.TYPE_OFFER && it.offer != null && it.offer.panel != null
        }?.let { notification ->
            Notification(
                notification.id,
                notification.offer!!.iconUrl,
                visitedOffers.contains(notification.id)
            )
        }
    }.distinctUntilChanged()

    fun onOpenOffer(offerNotification: Notification) {
        visitedOffersObservable.value!!.add(offerNotification.id)
        visitedOffersObservable.value = visitedOffersObservable.value
        Storage.save(visitedOffersObservable.value)
        eventOpenPromoOffer.tryEmit(offerNotification.id)
    }
}
