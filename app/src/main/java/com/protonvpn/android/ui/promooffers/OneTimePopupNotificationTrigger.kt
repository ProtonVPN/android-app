/*
 * Copyright (c) 2022. Proton AG
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

import android.app.Activity
import com.protonvpn.android.appconfig.ApiNotification
import com.protonvpn.android.appconfig.ApiNotificationManager
import com.protonvpn.android.appconfig.ApiNotificationTypes
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.ui.ForegroundActivityTracker
import dagger.Reusable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.scan
import javax.inject.Inject
import javax.inject.Singleton

@Reusable
class PromoActivityOpener @Inject constructor() {
    fun open(activity: Activity, notificationId: String) {
        activity.startActivity(PromoOfferActivity.createIntent(activity, notificationId))
    }
}

@Singleton
class OneTimePopupNotificationTrigger @Inject constructor(
    mainScope: CoroutineScope,
    foregroundActivityTracker: ForegroundActivityTracker,
    private val apiNotificationManager: ApiNotificationManager,
    currentUser: CurrentUser,
    private val promoOffersPrefs: PromoOffersPrefs,
    private val promoActivityOpener: PromoActivityOpener
) {
    init {
        // Don't use scan() with foregroundActivityFlow because it will leak activities.
        foregroundActivityTracker.isInForegroundFlow
            .scan(Pair(false, false)) { (_, wasInForeground), inForeground ->
                Pair(wasInForeground, inForeground)
            }
            .drop(1) // Scan's initial value can be ignored.
            .onEach { (wasInForeground, isInForeground) ->
                val loggedIn = currentUser.isLoggedIn()
                val foregroundActiviy = foregroundActivityTracker.foregroundActivity
                if (loggedIn && !wasInForeground && isInForeground && foregroundActiviy != null) {
                    onOneTimeNotificationOpportunity(foregroundActiviy)
                }
            }
            .launchIn(mainScope)
    }

    private suspend fun onOneTimeNotificationOpportunity(activity: Activity) {
        val activeNotifications = apiNotificationManager.activeListFlow.first()
        val oneTimeNotification = activeNotifications.firstOrNull { notification ->
            notification.isOneTimeNotification() && !promoOffersPrefs.visitedOffers.contains(notification.id)
        }
        if (oneTimeNotification != null) {
            promoOffersPrefs.addVisitedOffer(oneTimeNotification.id)
            promoActivityOpener.open(activity, oneTimeNotification.id)
        }
    }

    private fun ApiNotification.isOneTimeNotification(): Boolean =
        type == ApiNotificationTypes.TYPE_ONE_TIME_POPUP && offer != null && offer.panel != null
}
