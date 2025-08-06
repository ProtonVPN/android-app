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
import kotlinx.coroutines.delay
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

@Reusable
class PromoIapActivityOpener @Inject constructor() {
    fun open(activity: Activity, notificationId: String) {
        PromoOfferIapActivity.launch(activity, notificationId)
    }
}

@Reusable
class NpsActivityOpener @Inject constructor() {
    fun open(activity: Activity, notificationId: String) {
        activity.startActivity(NpsActivity.createIntent(activity, notificationId))
    }
}

@Singleton
class OneTimePopupNotificationTrigger @Inject constructor(
    mainScope: CoroutineScope,
    foregroundActivityTracker: ForegroundActivityTracker,
    private val apiNotificationManager: ApiNotificationManager,
    currentUser: CurrentUser,
    private val promoOffersPrefs: PromoOffersPrefs,
    private val promoActivityOpener: PromoActivityOpener,
    private val promoIapOpener: PromoIapActivityOpener,
    private val npsActivityOpener: NpsActivityOpener
) {

    private val NPS_NOTIFICATION_DELAY = 2000L

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
        val oneTimeNotification = apiNotificationManager.activeListFlow
            .first()
            .firstOrNull { notification ->
                (notification.isOneTimeNotification() || notification.isNpsType() || notification.isOneTimeIap()) &&
                        !promoOffersPrefs.visitedOffers.contains(notification.id)
            }

        oneTimeNotification?.let {
            promoOffersPrefs.addVisitedOffer(it.id)

            when {
                it.isNpsType() -> {
                    delay(NPS_NOTIFICATION_DELAY)
                    npsActivityOpener.open(activity, it.id)
                }

                it.isOneTimeIap() -> {
                    // TODO: make sure the user is still eligible for the offer!
                    promoIapOpener.open(activity, it.id)
                }

                else -> {
                    promoActivityOpener.open(activity, it.id)
                }
            }
        }
    }

    private fun ApiNotification.isNpsType(): Boolean =
        type == ApiNotificationTypes.TYPE_NPS

    private fun ApiNotification.isOneTimeNotification(): Boolean =
        type == ApiNotificationTypes.TYPE_ONE_TIME_POPUP && offer != null && offer.panel != null

    private fun ApiNotification.isOneTimeIap(): Boolean =
        type == ApiNotificationTypes.TYPE_INTERNAL_ONE_TIME_IAP_POPUP && offer != null && offer.panel != null &&
            offer.panel.fullScreenImage != null && offer.panel.button != null
}
