/*
 * Copyright (c) 2025 Proton AG
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

import android.content.Intent
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import com.protonvpn.android.redesign.recents.data.RecentsDao
import com.protonvpn.android.redesign.recents.usecases.GetQuickConnectIntent
import com.protonvpn.android.redesign.vpn.ConnectIntent
import com.protonvpn.android.vpn.ConnectTrigger
import com.protonvpn.android.vpn.DisconnectTrigger
import com.protonvpn.android.vpn.VpnConnectionManager
import com.protonvpn.android.vpn.VpnUiDelegate
import dagger.Reusable
import javax.inject.Inject

@Reusable
class WidgetActionHandler @Inject constructor(
    private val getQuickConnectIntent: GetQuickConnectIntent,
    private val vpnConnectionManager: VpnConnectionManager,
    private val recentDao: RecentsDao
) {
    suspend fun connectInBackground(recentId: Long?) {
        vpnConnectionManager.connectInBackground(
            intentForRecentId(recentId),
            triggerForRecentId(recentId)
        )
    }

    suspend fun connect(uiDelegate: VpnUiDelegate, recentId: Long?) {
        vpnConnectionManager.connect(
            uiDelegate,
            intentForRecentId(recentId),
            triggerForRecentId(recentId)
        )
    }

    fun disconnect() {
        vpnConnectionManager.disconnect(DisconnectTrigger.Widget)
    }

    private suspend fun intentForRecentId(recentId: Long?): ConnectIntent {
        val recent = recentId?.let { recentDao.getById(it) }
        return recent?.connectIntent ?: getQuickConnectIntent()
    }

    private fun triggerForRecentId(recentId: Long?) =
        ConnectTrigger.Widget(if (recentId != null) "widget recent" else "widget")

    suspend fun onIntent(uiDelegate: VpnUiDelegate, intent: Intent) {
        val connect = intent.hasExtra(EXTRA_ACTION_CONNECT)
        if (connect) {
            val recentId = intent.getLongExtra(EXTRA_CONNECT_RECENT_ID, -1).takeIf { it >= 0 }
            connect(uiDelegate, recentId)
        }
    }

    companion object {
        const val EXTRA_ACTION_CONNECT = "action_connect"
        const val EXTRA_CONNECT_RECENT_ID = "recent_id"

        fun connectActionParameters(recentId: Long?): ActionParameters {
            val connect = ActionParameters.Key<Boolean>(EXTRA_ACTION_CONNECT) to true
            return if (recentId == null)
                actionParametersOf(connect)
            else
                actionParametersOf(connect, ActionParameters.Key<Long>(EXTRA_CONNECT_RECENT_ID) to recentId)
        }
    }
}
