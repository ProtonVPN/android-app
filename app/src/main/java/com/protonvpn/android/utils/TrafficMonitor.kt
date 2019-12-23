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

import android.net.TrafficStats
import androidx.lifecycle.MutableLiveData
import com.protonvpn.android.bus.TrafficUpdate
import com.protonvpn.android.vpn.VpnStateMonitor
import java.util.*

class TrafficMonitor
private constructor() {
    private var totalDownloaded: Long = 0
    private var totalUploaded: Long = 0
    private var sessionDownloaded: Long = 0
    private var sessionUploaded: Long = 0
    private var sessionTimeInSeconds = 0
    private var timer: Timer? = null

    val trafficStatus = MutableLiveData<TrafficUpdate>().apply {
        value = TrafficUpdate(0, 0, 0, 0, 0)
    }

    fun bindTrafficMonitor(vpnStateMonitor: VpnStateMonitor) {
        vpnStateMonitor.vpnState.observeForever {
            stateChanged(it.state)
        }
    }

    private fun startMonitor() {
        resetData()
        startThread()
    }

    private fun resetData() {
        totalDownloaded = 0
        totalUploaded = 0
        sessionDownloaded = 0
        sessionUploaded = 0
        sessionTimeInSeconds = 0
    }

    private fun stopMonitor() {
        resetData()
        if (timer != null) {
            timer!!.cancel()
            timer = null
        }
    }

    private fun startThread() {
        if (timer == null) {
            val firstTime = booleanArrayOf(true)
            timer = Timer()
            timer!!.scheduleAtFixedRate(object : TimerTask() {

                override fun run() {
                    // Speeds need to be divided by two due to TrafficStats calculating both phone and VPN
                    // interfaces which leads to doubled data. NetworkStatsManager may have solved this
                    // problem but is only available from marshmallow.
                    val downloadSpeed = (TrafficStats.getTotalRxBytes() - totalDownloaded) / 2
                    val uploadSpeed = (TrafficStats.getTotalTxBytes() - totalUploaded) / 2
                    totalDownloaded = TrafficStats.getTotalRxBytes()
                    totalUploaded = TrafficStats.getTotalTxBytes()
                    sessionTimeInSeconds++
                    if (firstTime[0]) {
                        firstTime[0] = false
                    } else {
                        sessionDownloaded += downloadSpeed
                        sessionUploaded += uploadSpeed
                        trafficStatus.postValue(TrafficUpdate(downloadSpeed, uploadSpeed,
                                sessionDownloaded, sessionUploaded, sessionTimeInSeconds))
                    }
                }
            }, 0, 1000)
        }
    }

    private fun stateChanged(state: VpnStateMonitor.State) {
        when (state) {
            VpnStateMonitor.State.CONNECTED -> startMonitor()
            else -> stopMonitor()
        }
    }

    companion object {

        val instance = TrafficMonitor()
    }
}
