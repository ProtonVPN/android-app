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

import androidx.lifecycle.ViewModel
import com.protonvpn.android.appconfig.ApiNotificationManager
import com.protonvpn.android.appconfig.ApiNotificationTypes
import com.protonvpn.android.utils.Storage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject

@HiltViewModel
class PromoOfferNotificationViewModel @Inject constructor(
    private val apiNotificationManager: ApiNotificationManager,
) : ViewModel() {

    // Represents a notification that is opened in a separate screen.
    data class PanelNotification(
        val notificationId: String,
        val pictureUrlForPreload: String?
    )

    // Represents the notification icon in the toolbar.
    data class ToolbarNotification(
        val notificationId: String,
        val iconUrl: String?,
        val showUnreadBadge: Boolean,
        val openUrl: String?,
        val panel: PanelNotification?
    )

    // Distinct class needed so that storage can use it as key.
    private class VisitedOffers : HashSet<String> {
        constructor() : super()
        constructor(collection: Collection<String>) : super(collection)
    }
    private val visitedOffersFlow =
        MutableStateFlow<VisitedOffers>(Storage.load(VisitedOffers::class.java, VisitedOffers()))

    val eventOpenPanelNotification = MutableSharedFlow<PanelNotification>(extraBufferCapacity = 1)
    val toolbarNotifications get() = combine(
        apiNotificationManager.activeListFlow,
        visitedOffersFlow
    ) { notifications, visitedOffers ->
        notifications.firstOrNull {
            it.type == ApiNotificationTypes.TYPE_OFFER && it.offer != null &&
                (it.offer.panel != null || it.offer.url != null)
        }?.let { notification ->
            val panel = notification.offer!!.panel?.let {
                PanelNotification(notification.id, it.pictureUrl)
            }
            ToolbarNotification(
                notification.id,
                notification.offer.iconUrl,
                !visitedOffers.contains(notification.id),
                notification.offer.url,
                panel
            )
        }
    }.distinctUntilChanged()

    fun onOpenPanel(panelNotification: PanelNotification) {
        markVisited(panelNotification.notificationId)
        eventOpenPanelNotification.tryEmit(panelNotification)
    }

    fun onOpenNotificationUrl(notificationId: String) = markVisited(notificationId)

    private fun markVisited(notificationId: String) {
        visitedOffersFlow.value = VisitedOffers(visitedOffersFlow.value + notificationId)
        Storage.save(visitedOffersFlow.value)
    }
}
