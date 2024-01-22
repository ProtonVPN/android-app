/*
 * Copyright (c) 2018 Proton Technologies AG
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
package com.protonvpn.android.components

import android.app.ActivityManager
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build.VERSION_CODES
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import androidx.core.content.getSystemService
import com.protonvpn.android.R
import com.protonvpn.android.quicktile.QuickTileActionReceiver
import com.protonvpn.android.quicktile.QuickTileDataStore
import com.protonvpn.android.utils.tickFlow
import com.protonvpn.android.utils.vpnProcessRunning
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

@AndroidEntryPoint
@RequiresApi(VERSION_CODES.N)
class QuickTileService : TileService() {

    // NOTE: this service runs in a separate, light process, be mindful of that when adding
    // dependencies.
    @Inject lateinit var dataStore: QuickTileDataStore
    @Inject lateinit var mainScope: CoroutineScope

    private var listeningScope : CoroutineScope? = null

    override fun onStartListening() {
        super.onStartListening()
        listeningScope = CoroutineScope(SupervisorJob())

        val tile = qsTile
        if (tile != null) {
            tile.icon = Icon.createWithResource(this, R.drawable.ic_vpn_status_information)
            bindToListener()
        }
    }

    override fun onStopListening() {
        listeningScope?.cancel()
        listeningScope = null
        super.onStopListening()
    }

    private fun bindToListener() {
        val activityManager = getSystemService<ActivityManager>()
        listeningScope?.launch {
            combine(
                dataStore.getDataFlow(),
                tickFlow(10.seconds, System::currentTimeMillis)
            ) { data, _ ->
                val vpnProcessRunning = activityManager?.vpnProcessRunning(this@QuickTileService)
                if (vpnProcessRunning == true)
                    data
                else
                    data.copy(state = QuickTileDataStore.TileState.Disabled)
            }.collect {
                stateChanged(it)
            }
        }
    }

    override fun onClick() {
        if (isLocked) {
            unlockAndRun {
                onClickInternal()
            }
        } else {
            onClickInternal()
        }
    }

    private fun broadcastTileAction(action: String) {
        val intent = Intent(this, QuickTileActionReceiver::class.java)
        intent.action = action
        sendBroadcast(intent)
    }

    private fun onClickInternal() {
        val isActive = qsTile.state == Tile.STATE_ACTIVE
        mainScope.launch {
                broadcastTileAction(
                    if (isActive)
                        QuickTileActionReceiver.ACTION_DISCONNECT
                    else
                        QuickTileActionReceiver.ACTION_CONNECT
                )
        }
    }

    private fun stateChanged(data: QuickTileDataStore.Data) {
        when (data.state) {
            QuickTileDataStore.TileState.Disabled -> {
                qsTile.label = getString(if (data.isLoggedIn) R.string.quickConnect else R.string.login)
                qsTile.state = Tile.STATE_INACTIVE
            }
            QuickTileDataStore.TileState.Connecting -> {
                qsTile.label = getString(R.string.state_connecting)
                qsTile.state = Tile.STATE_ACTIVE
            }
            QuickTileDataStore.TileState.WaitingForNetwork -> {
                qsTile.label = getString(R.string.loaderReconnectNoNetwork)
                qsTile.state = Tile.STATE_ACTIVE
            }
            QuickTileDataStore.TileState.Error -> {
                qsTile.label = getString(R.string.state_error)
                qsTile.state = Tile.STATE_UNAVAILABLE
            }
            QuickTileDataStore.TileState.Connected -> {
                qsTile.label = getString(R.string.tileConnected, data.serverName)
                qsTile.state = Tile.STATE_ACTIVE
            }
            QuickTileDataStore.TileState.Disconnecting -> {
                qsTile.label = getString(R.string.state_disconnecting)
                qsTile.state = Tile.STATE_UNAVAILABLE
            }
        }
        qsTile.updateTile()
    }
}
