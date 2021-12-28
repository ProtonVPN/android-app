/*
 * Copyright (c) 2019 Proton Technologies AG
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
package com.protonvpn.android.utils

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.TrafficStats
import android.os.PowerManager
import androidx.core.content.getSystemService
import androidx.lifecycle.MutableLiveData
import com.protonvpn.android.bus.TrafficUpdate
import com.protonvpn.android.logging.LogCategory
import com.protonvpn.android.logging.ProtonLogger
import com.protonvpn.android.utils.AndroidUtils.registerBroadcastReceiver
import com.protonvpn.android.vpn.ConnectivityMonitor
import com.protonvpn.android.vpn.VpnState
import com.protonvpn.android.vpn.VpnStateMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import kotlin.math.roundToLong

class TrafficMonitor constructor(
    val context: Context,
    val scope: CoroutineScope,
    val now: () -> Long,
    val vpnStateMonitor: VpnStateMonitor,
    private val connectivityMonitor: ConnectivityMonitor,
) {
    companion object {
        const val TRAFFIC_HISTORY_LENGTH_S = 31L
    }

    val trafficStatus = MutableLiveData<TrafficUpdate?>()

    val trafficHistory = MutableLiveData<List<TrafficUpdate>>(emptyList())

    private var sessionStart = 0L
    private var sessionDownloaded = 0L
    private var sessionUploaded = 0L
    private var lastTotalDownload = 0L
    private var lastTotalUpload = 0L
    private var lastTimestamp = 0L

    private var updateJob: Job? = null

    init {
        scope.launch {
            vpnStateMonitor.status.collect {
                stateChanged(it.state)
            }
        }
        scope.launch {
            vpnStateMonitor.newSessionEvent.collect {
                resetSession()
            }
        }

        context.registerBroadcastReceiver(IntentFilter(Intent.ACTION_SCREEN_OFF)) {
            ProtonLogger.logCustom(LogCategory.UI, "Screen off")
            stopUpdateJob()
        }
        context.registerBroadcastReceiver(IntentFilter(Intent.ACTION_SCREEN_ON)) {
            ProtonLogger.logCustom(LogCategory.UI, "Screen on")
            if (vpnStateMonitor.state == VpnState.Connected)
                startUpdateJob()
        }
    }

    private fun resetSession() {
        trafficStatus.value = TrafficUpdate(0, 0, 0, 0, 0, 0)
        trafficHistory.value = emptyList()

        lastTimestamp = now()
        lastTotalDownload = TrafficStats.getTotalRxBytes()
        lastTotalUpload = TrafficStats.getTotalTxBytes()

        sessionDownloaded = 0L
        sessionUploaded = 0L
        sessionStart = lastTimestamp
    }

    private fun startUpdating() {
        endUpdating()
        if (context.getSystemService<PowerManager>()?.isInteractive != false)
            startUpdateJob()
    }

    private fun endUpdating() {
        trafficStatus.value = null
        stopUpdateJob()
    }

    private fun startUpdateJob() {
        if (updateJob == null) {
            updateJob = scope.launch(Dispatchers.Main) {
                while (true) {
                    val timestamp = now()
                    val elapsedMillis = timestamp - lastTimestamp
                    val elapsedSeconds = elapsedMillis / 1000f

                    // Speeds need to be divided by two due to TrafficStats calculating both phone and VPN
                    // interfaces which leads to doubled data. NetworkStatsManager may have solved this
                    // problem but is only available from marshmallow.
                    val totalDownload = TrafficStats.getTotalRxBytes()
                    val totalUpload = TrafficStats.getTotalTxBytes()
                    val downloaded = (totalDownload - lastTotalDownload).coerceAtLeast(0) / 2
                    val uploaded = (totalUpload - lastTotalUpload).coerceAtLeast(0) / 2
                    val downloadSpeed = (downloaded / elapsedSeconds).roundToLong()
                    val uploadSpeed = (uploaded / elapsedSeconds).roundToLong()

                    sessionDownloaded += downloaded
                    sessionUploaded += uploaded

                    val sessionTimeSeconds = (timestamp - sessionStart).toInt() / 1000
                    val update = TrafficUpdate(
                        timestamp, downloadSpeed, uploadSpeed, sessionDownloaded,
                        sessionUploaded, sessionTimeSeconds
                    )
                    trafficStatus.value = update
                    trafficHistory.value = (trafficHistory.value!! + update)
                        .takeLastWhile {
                            it.timestampMs > timestamp - TimeUnit.SECONDS.toMillis(TRAFFIC_HISTORY_LENGTH_S)
                        }

                    lastTotalDownload = totalDownload
                    lastTotalUpload = totalUpload
                    lastTimestamp = timestamp

                    delay(1000)
                }
            }
        }
    }

    private fun stopUpdateJob() {
        updateJob?.cancel()
        updateJob = null
        trafficHistory.value = emptyList()
    }

    private fun stateChanged(state: VpnState) {
        if (state == VpnState.Connected)
            startUpdating()
        else
            endUpdating()
    }
}
