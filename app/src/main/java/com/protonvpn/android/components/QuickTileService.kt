/*
 * Copyright (c) 2018 Proton AG
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

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Build.VERSION_CODES
import android.os.SystemClock
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import androidx.annotation.StringRes
import androidx.core.content.getSystemService
import com.protonvpn.android.R
import com.protonvpn.android.quicktile.QuickTileActionReceiver
import com.protonvpn.android.quicktile.QuickTileDataStore
import com.protonvpn.android.utils.tickFlow
import com.protonvpn.android.utils.vpnProcessRunning
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@AndroidEntryPoint
@RequiresApi(VERSION_CODES.N)
class QuickTileService : TileService() {

    // NOTE: this service runs in a separate, light process, be mindful of that when adding
    // dependencies.
    @Inject lateinit var dataStore: QuickTileDataStore
    @Inject lateinit var mainScope: CoroutineScope

    private var lastClickTimestamp = 0L
    private var listeningScope: CoroutineScope? = null
    private val stateOverrideFlow by lazy(LazyThreadSafetyMode.NONE) { ConnectStateOverride(mainScope) }

    override fun onStartListening() {
        super.onStartListening()
        listeningScope = CoroutineScope(SupervisorJob())

        val tile = qsTile
        if (tile != null) {
            tile.icon = Icon.createWithResource(this, R.drawable.quick_tile)
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
            val vpnStateFlow =
                combine(
                    dataStore.getDataFlow(),
                    tickFlow(10.seconds, System::currentTimeMillis)
                ) { data, _ ->
                    val vpnProcessRunning = activityManager?.vpnProcessRunning(this@QuickTileService)
                    if (vpnProcessRunning == true) {
                        data
                    } else {
                        data.copy(state = QuickTileDataStore.TileState.Disabled)
                    }
                }
                // The main process will emit Disabled state while starting. Only react to distinct states.
                .distinctUntilChanged()

            val cancelOverrideFlow = vpnStateFlow
                // When tray is reopened it will restart the flows. Don't stop the override on the initial state.
                .drop(1)
                .onEach { stateOverrideFlow.stop() }

            val updateTileFlow =
                combine(vpnStateFlow, stateOverrideFlow) { vpnState, override ->
                    override ?: vpnState
                }
                .onEach { stateChanged(it) }

            cancelOverrideFlow.launchIn(this)
            updateTileFlow.launchIn(this)
        }
    }

    override fun onClick() {
        // The QuickTileService process is lightweight and should start quickly. If it's not fast enough and the user
        // taps the icon multiple times then only react to the first tap.
        if (isClickTooFast()) return
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
        val shouldConnect = qsTile.state != Tile.STATE_ACTIVE
        mainScope.launch {
            val tileData = dataStore.getData()
            val isLoggedIn = tileData.isLoggedIn
            if (!isLoggedIn) {
                launchApp()
            } else {
                if (shouldConnect) { // Set state immediately in case it takes long to launch the main process.
                    stateOverrideFlow.start(
                        QuickTileDataStore.Data(
                            QuickTileDataStore.TileState.Connecting,
                            isLoggedIn = true,
                            isAutoOpenForDefaultConnection = tileData.isAutoOpenForDefaultConnection
                        )
                    )
                }
                broadcastTileAction(
                    if (shouldConnect)
                        QuickTileActionReceiver.ACTION_CONNECT
                    else
                        QuickTileActionReceiver.ACTION_DISCONNECT
                )
                if (shouldConnect && tileData.isAutoOpenForDefaultConnection)
                    launchApp()
            }
        }
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun launchApp() {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(
                PendingIntent.getActivity(this, 0, launchIntent, PendingIntent.FLAG_IMMUTABLE))
        } else {
            startActivityAndCollapse(launchIntent)
        }
    }

    private fun stateChanged(data: QuickTileDataStore.Data) {
        when (data.state) {
            QuickTileDataStore.TileState.Disabled ->
                updateTileState(Tile.STATE_INACTIVE, if (data.isLoggedIn) R.string.quickConnect else R.string.login)
            QuickTileDataStore.TileState.Connecting ->
                updateTileState(Tile.STATE_ACTIVE, R.string.state_connecting)
            QuickTileDataStore.TileState.WaitingForNetwork ->
                updateTileState(Tile.STATE_ACTIVE, R.string.loaderReconnectNoNetwork)
            QuickTileDataStore.TileState.Error ->
                updateTileState(Tile.STATE_UNAVAILABLE, R.string.state_error)
            QuickTileDataStore.TileState.Connected ->
                updateTileState(Tile.STATE_ACTIVE, getString(R.string.tileConnected, data.serverName))
            QuickTileDataStore.TileState.Disconnecting ->
                updateTileState(Tile.STATE_UNAVAILABLE, R.string.state_disconnecting)
        }
    }

    private fun updateTileState(state: Int, @StringRes labelRes: Int) {
        updateTileState(state, getString(labelRes))
    }

    private fun updateTileState(state: Int, label: String) {
        qsTile.state = state
        qsTile.label = label
        qsTile.updateTile()
    }

    private fun isClickTooFast(): Boolean {
        val now = SystemClock.elapsedRealtime()
        val isTooFast = now - lastClickTimestamp < 75L
        if (!isTooFast) {
            lastClickTimestamp = now
        }
        return isTooFast
    }

    private class ConnectStateOverride(private val scope: CoroutineScope): Flow<QuickTileDataStore.Data?> {
        private val state = MutableStateFlow<QuickTileDataStore.Data?>(null)
        private var job: Job? = null

        fun start(overrideState: QuickTileDataStore.Data, timeout: Duration = 10.seconds) {
            job?.cancel()
            job = scope.launch {
                try {
                    state.value = overrideState
                    delay(timeout)
                } finally {
                    state.value = null
                }
            }
        }

        fun stop() {
            job?.cancel()
        }

        override suspend fun collect(collector: FlowCollector<QuickTileDataStore.Data?>) = state.collect(collector)
    }
}
