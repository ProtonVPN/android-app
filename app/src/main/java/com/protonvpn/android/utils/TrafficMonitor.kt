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
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.protonvpn.android.bus.TrafficUpdate
import com.protonvpn.android.utils.AndroidUtils.registerBroadcastReceiver
import com.protonvpn.android.vpn.VpnStateMonitor
import com.protonvpn.android.vpn.VpnState
import kotlin.math.roundToLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class TrafficMonitor(
    val context: Context,
    val scope: CoroutineScope,
    val now: () -> Long
) {
    val trafficStatus = MutableLiveData<TrafficUpdate>()

    private var sessionStart = 0L
    private var sessionDownloaded = 0L
    private var sessionUploaded = 0L
    private var lastTotalDownload = 0L
    private var lastTotalUpload = 0L
    private var lastTimestamp = 0L

    private var updateJob: Job? = null

    fun init(vpnStatus: LiveData<VpnStateMonitor.Status>) {
        vpnStatus.observeForever {
            stateChanged(it.state)
        }
        context.registerBroadcastReceiver(IntentFilter(Intent.ACTION_SCREEN_OFF)) {
            stopUpdateJob()
        }
        context.registerBroadcastReceiver(IntentFilter(Intent.ACTION_SCREEN_ON)) {
            if (vpnStatus.value?.state == VpnState.Connected)
                startUpdateJob()
        }
    }

    private fun startSession() {
        endSession()
        trafficStatus.value = TrafficUpdate(0, 0, 0, 0, 0)

        lastTimestamp = now()
        lastTotalDownload = TrafficStats.getTotalRxBytes()
        lastTotalUpload = TrafficStats.getTotalTxBytes()

        sessionDownloaded = 0L
        sessionUploaded = 0L
        sessionStart = lastTimestamp

        if (context.getSystemService<PowerManager>()?.isInteractive != false)
            startUpdateJob()
    }

    private fun endSession() {
        trafficStatus.value = null
        stopUpdateJob()
    }

    private fun startUpdateJob() {
        if (updateJob == null) {
            updateJob = scope.launch(Dispatchers.Main) {
                while (true) {
                    delay(1000)

                    val timestamp = now()
                    val elapsedSeconds = (timestamp - lastTimestamp) / 1000f

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
                    trafficStatus.value = TrafficUpdate(downloadSpeed, uploadSpeed, sessionDownloaded,
                            sessionUploaded, sessionTimeSeconds)

                    lastTotalDownload = totalDownload
                    lastTotalUpload = totalUpload
                    lastTimestamp = timestamp
                }
            }
        }
    }

    private fun stopUpdateJob() {
        updateJob?.cancel()
        updateJob = null
    }

    private fun stateChanged(state: VpnState) {
        if (state == VpnState.Connected)
            startSession()
        else
            endSession()
    }
}
