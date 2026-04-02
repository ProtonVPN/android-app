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

package com.protonvpn.android.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Context.NOTIFICATION_SERVICE
import android.os.Build
import com.protonvpn.android.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

private const val PREFIX = "com.protonvpn.android"

@Singleton
class NotificationChannels @Inject constructor(
   @ApplicationContext private val appContext: Context
) {
    val idConnectionStatus = PREFIX
    val idConnectionErrors = "$PREFIX.connection_errors"
    val idConnectionTips = "$PREFIX.connection_tips"

    private class ChannelConfig(
        val id: String,
        val nameRes: Int,
        val descriptionRes: Int,
        val importance: Int,
        val showBadge: Boolean,
        val lockscreenVisibility: Int = Notification.VISIBILITY_PUBLIC,
    )

    private val channelConfigurations = listOf(
        ChannelConfig(
            id = idConnectionStatus,
            nameRes = R.string.notifications_channel_connection_status_name,
            descriptionRes = R.string.notifications_channel_connection_status_description,
            importance = NotificationManager.IMPORTANCE_LOW,
            showBadge = false,
        ),
        ChannelConfig(
            id = idConnectionErrors,
            nameRes = R.string.notifications_channel_connection_errors_name,
            descriptionRes = R.string.notifications_channel_connection_errors_description,
            importance = NotificationManager.IMPORTANCE_HIGH,
            showBadge = true,
        ),
        ChannelConfig(
            id = idConnectionTips,
            nameRes = R.string.notifications_channel_connection_tips_name,
            descriptionRes = R.string.notifications_channel_connection_tips_description,
            importance = NotificationManager.IMPORTANCE_DEFAULT,
            showBadge = true,
        ),
    )

    init {
        updateChannels(appContext)
    }

    fun updateChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager =
                context.getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            val notificationChannels = channelConfigurations.map { config ->
                NotificationChannel(
                    config.id,
                    context.getString(config.nameRes),
                    config.importance
                ).apply {
                    description = context.getString(config.descriptionRes)
                    lockscreenVisibility = config.lockscreenVisibility
                    setShowBadge(config.showBadge)
                }
            }
            manager.createNotificationChannels(notificationChannels)
        }
    }

    fun updateTranslations(context: Context) = updateChannels(context)
}
