/*
 * Copyright (c) 2026. Proton AG
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

package com.protonvpn.android.restrictonsupsell

import android.app.PendingIntent
import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.protonvpn.android.R
import com.protonvpn.android.di.WallClock
import com.protonvpn.android.notifications.NotificationHelper
import com.protonvpn.android.redesign.app.ui.CreateLaunchIntent
import com.protonvpn.android.redesign.app.ui.MainActivity
import com.protonvpn.android.utils.Constants
import dagger.Reusable
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.days

private val NotificationRepeatIntervalMs = 1.days.inWholeMilliseconds

@Reusable
class ShowStreamingUpsellNotification @Inject constructor(
    @param:ApplicationContext private val appContext: Context,
    private val notificationManager: NotificationManagerCompat,
    private val createLaunchIntent: CreateLaunchIntent,
) {
    operator fun invoke() {
        if (!notificationManager.areNotificationsEnabled()) return

        val launchIntent = createLaunchIntent.forNotification().apply {
            putExtra(MainActivity.EXTRA_SHOW_STREAMING_RESTRICTION_UPSELL, true)
        }
        val pendingIntent = PendingIntent.getActivity(
            appContext,
            // Use a unique request code to avoid PendingIntent merging.
            Constants.NOTIFICATION_STREAMING_BLOCKED_ID,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(appContext, NotificationHelper.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_vpn_status_disconnected)
            .setContentTitle(appContext.getString(R.string.notification_streaming_blocked_title))
            .setContentText(appContext.getString(R.string.notification_streaming_blocked_text))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(Constants.NOTIFICATION_STREAMING_BLOCKED_ID, notification)
    }
}

@Singleton
class StreamingUpsellRestrictionsNotificationTrigger @Inject constructor(
    private val mainScope: CoroutineScope,
    eventStreamingRestricted: StreamingUpsellRestrictionsFlow,
    private val restrictionsUpsellStore: RestrictionsUpsellStore,
    private val showNotification: ShowStreamingUpsellNotification,
    @param:WallClock private val now: () -> Long,
) {

    @VisibleForTesting
    val eventNotification =
        eventStreamingRestricted.filter {
            // Don't use restrictionsUpsellStore.state in combine to avoid initializing the store
            // on app start.
            val lastNotificationTimestamp =
                restrictionsUpsellStore.state.first().streaming.lastNotificationTimestamp
            lastNotificationTimestamp + NotificationRepeatIntervalMs <= now()
        }

    fun start() {
        eventNotification
            .onEach {
                showNotification()
                markNotificationShown()
            }
            .launchIn(mainScope)
    }

    private suspend fun markNotificationShown() {
        restrictionsUpsellStore.update { current ->
            current.copy(
                streaming = current.streaming.copy(lastNotificationTimestamp = now())
            )
        }
    }
}
