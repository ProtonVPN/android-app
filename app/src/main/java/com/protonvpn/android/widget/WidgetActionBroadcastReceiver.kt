/*
 * Copyright (c) 2024. Proton AG
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

package com.protonvpn.android.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.protonvpn.android.utils.launchAsyncReceive
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import javax.inject.Inject

@AndroidEntryPoint
class WidgetActionBroadcastReceiver : BroadcastReceiver() {

    @Inject lateinit var mainScope: CoroutineScope
    @Inject lateinit var widgetActionHandler: WidgetActionHandler

    override fun onReceive(context: Context, intent: Intent) = launchAsyncReceive(mainScope) {
        when (intent.action) {
            ACTION_CONNECT -> widgetActionHandler.connectInBackground(
                intent.getLongExtra(EXTRA_RECENT_ID, -1).takeIf { it >= 0 }
            )
            ACTION_DISCONNECT -> widgetActionHandler.disconnect()
        }
    }

    companion object {
        private const val EXTRA_RECENT_ID = "recent id"
        private const val ACTION_CONNECT = "connect"
        private const val ACTION_DISCONNECT = "disconnect"

        fun intentConnect(context: Context, recentId: Long? = null) =
            Intent(context, WidgetActionBroadcastReceiver::class.java).apply {
                action = ACTION_CONNECT
                putExtra(EXTRA_RECENT_ID, recentId)
            }

        fun intentDisconnect(context: Context) = Intent(context, WidgetActionBroadcastReceiver::class.java).apply {
            action = ACTION_DISCONNECT
        }
    }
}
