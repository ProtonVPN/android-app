/*
 * Copyright (c) 2020 Proton Technologies AG
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
package com.protonvpn.android.ui.home.profiles

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.map
import com.protonvpn.android.appconfig.ApiNotificationManager
import com.protonvpn.android.appconfig.ApiNotificationTypes
import com.protonvpn.android.utils.Storage
import com.protonvpn.android.utils.eagerMapNotNull
import javax.inject.Inject

class HomeViewModel @Inject constructor(
    private val apiNotificationManager: ApiNotificationManager
) : ViewModel() {

    inner class OfferViewModel(
        private val id: String,
        val label: String,
        val url: String,
        val iconUrl: String,
        val visited: Boolean
    ) {
        fun setVisited() = setOfferVisited(id)
    }

    // Wrapper class needed so that storage can use its class as key
    private class VisitedOffers(val visited: MutableSet<String>)
    private var visitedOffersObservable =
            MutableLiveData(Storage.load(VisitedOffers::class.java, VisitedOffers(mutableSetOf())))
    private val visitedOffers get() = visitedOffersObservable.value!!.visited

    val offersViewModel get() = apiNotificationManager.activeListObservable.eagerMapNotNull {
        createApiNotificationViewModel()
    }.apply {
        addSource(visitedOffersObservable) { value = createApiNotificationViewModel() }
    }

    private fun createApiNotificationViewModel(): List<OfferViewModel> =
        apiNotificationManager.activeList.filter {
            it.type == ApiNotificationTypes.TYPE_OFFER
        }.mapNotNull { apiNotification ->
            apiNotification.offer?.let {
                OfferViewModel(apiNotification.id, it.label, it.url, it.iconUrl, apiNotification.id in visitedOffers)
            }
        }

    val haveNonVisitedOffers =
            offersViewModel.map { offer -> offer.any { !it.visited } }

    fun setOfferVisited(id: String) {
        visitedOffers.add(id)
        visitedOffersObservable.value = visitedOffersObservable.value
        Storage.save(visitedOffersObservable.value)
    }
}
