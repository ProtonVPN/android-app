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

import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build.VERSION_CODES
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import androidx.lifecycle.Observer
import com.protonvpn.android.R
import com.protonvpn.android.models.config.UserData
import com.protonvpn.android.ui.login.LoginActivity
import com.protonvpn.android.utils.ProtonLogger
import com.protonvpn.android.utils.ServerManager
import com.protonvpn.android.vpn.VpnState
import com.protonvpn.android.vpn.VpnStateMonitor
import dagger.android.AndroidInjection
import javax.inject.Inject

@RequiresApi(VERSION_CODES.N)
class QuickTileService : TileService() {

    @Inject lateinit var manager: ServerManager
    @Inject lateinit var userData: UserData
    @Inject lateinit var stateMonitor: VpnStateMonitor

    private val stateInfoObserver = Observer(::stateChanged)

    override fun onCreate() {
        super.onCreate()
        AndroidInjection.inject(this)
    }

    override fun onDestroy() {
        stateMonitor.vpnStatus.removeObserver(stateInfoObserver)
        super.onDestroy()
    }

    override fun onStartListening() {
        super.onStartListening()
        val tile = qsTile
        if (tile != null) {
            tile.icon = Icon.createWithResource(this, R.drawable.ic_proton)
            bindToListener()
        }
    }

    private fun bindToListener() {
        stateMonitor.vpnStatus.observeForever(stateInfoObserver)
    }

    override fun onClick() {
        if (qsTile.state == Tile.STATE_INACTIVE) {
            if (userData.isLoggedIn) {
                val profile = manager.defaultConnection
                if (profile != null) {
                    ProtonLogger.log("Connecting via quick tile")
                    stateMonitor.connect(this, profile)
                }
            } else {
                val intent = Intent(applicationContext, LoginActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
            }
        } else {
            ProtonLogger.log("Disconnecting via quick tile")
            stateMonitor.disconnect()
        }
    }

    private fun stateChanged(vpnStatus: VpnStateMonitor.Status) {
        when (vpnStatus.state) {
            VpnState.Disabled -> {
                qsTile.label = getString(if (userData.isLoggedIn) R.string.quickConnect else R.string.login)
                qsTile.state = Tile.STATE_INACTIVE
            }
            VpnState.CheckingAvailability,
            VpnState.ScanningPorts,
            VpnState.Connecting -> {
                qsTile.label = getString(R.string.state_connecting)
                qsTile.state = Tile.STATE_UNAVAILABLE
            }
            VpnState.Connected -> {
                val server = vpnStatus.server
                val serverName = server!!.serverName
                qsTile.label = getString(R.string.tileConnected, serverName)
                qsTile.state = Tile.STATE_ACTIVE
            }
            VpnState.Disconnecting -> {
                qsTile.label = getString(R.string.state_disconnecting)
                qsTile.state = Tile.STATE_UNAVAILABLE
            }
        }
        qsTile.updateTile()
    }
}
