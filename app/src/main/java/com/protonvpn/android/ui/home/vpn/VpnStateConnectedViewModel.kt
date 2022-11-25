/*
 * Copyright (c) 2021. Proton Technologies AG
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

package com.protonvpn.android.ui.home.vpn

import androidx.annotation.StringRes
import androidx.lifecycle.LiveData
import androidx.lifecycle.Transformations.map
import androidx.lifecycle.ViewModel
import com.github.mikephil.charting.data.Entry
import com.protonvpn.android.R
import com.protonvpn.android.bus.TrafficUpdate
import com.protonvpn.android.models.profiles.ProfileColor
import com.protonvpn.android.utils.ServerManager
import com.protonvpn.android.utils.TrafficMonitor
import com.protonvpn.android.vpn.VpnState
import com.protonvpn.android.vpn.VpnStateMonitor
import com.protonvpn.android.vpn.VpnStatusProviderUI
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.combine
import me.proton.core.presentation.utils.SnackType
import javax.inject.Inject

private const val MILLIS_IN_SECOND = 1000f
private const val BYTES_IN_KBYTE = 1024f

@HiltViewModel
class VpnStateConnectedViewModel @Inject constructor(
    private val vpnStatusProviderUI: VpnStatusProviderUI,
    vpnStateMonitor: VpnStateMonitor,
    private val serverManager: ServerManager,
    trafficMonitor: TrafficMonitor
) : ViewModel() {

    data class ConnectionState(
        val serverName: String,
        val serverLoad: Float,
        val exitIp: String,
        @StringRes val protocolDisplay: Int?
    )

    data class TrafficSpeedChartData(
        val uploadKpbsHistory: List<Entry>,
        val downloadKbpsHistory: List<Entry>
    )

    data class SnackbarNotification(@StringRes val text: Int, val type: SnackType)

    val eventNotification = MutableSharedFlow<SnackbarNotification>(extraBufferCapacity = 1)
    val connectionState = combine(
        vpnStatusProviderUI.status,
        vpnStateMonitor.exitIp,
        serverManager.serverListVersion,
    ) { status, exitIp, _ ->
        toConnectionState(status, exitIp)
    }
    val trafficSpeedKbpsHistory = speedHistoryToChartData(trafficMonitor.trafficHistory)

    fun saveToProfile() {
        vpnStatusProviderUI.connectionParams?.server?.let { currentServer ->
            val alreadySaved = serverManager.getSavedProfiles().any { it.wrapper.serverId == currentServer.serverId }
            if (alreadySaved) {
                val notification = SnackbarNotification(R.string.saveProfileAlreadySaved, SnackType.Norm)
                eventNotification.tryEmit(notification)
            } else {
                serverManager.addToProfileList(currentServer.serverName, ProfileColor.random(), currentServer)
                eventNotification.tryEmit(SnackbarNotification(R.string.toastProfileSaved, SnackType.Success))
            }
        }
    }

    private fun toConnectionState(vpnStatus: VpnStateMonitor.Status, exitIp: String?): ConnectionState =
        if (vpnStatus.state is VpnState.Connected) {
            with(requireNotNull(vpnStatus.connectionParams)) {
                // The server in ConnectionParams may be a stale object if the whole server list
                // is refreshed.
                val upToDateServer = serverManager.getServerById(server.serverId) ?: server
                ConnectionState(
                    upToDateServer.serverName,
                    upToDateServer.load,
                    exitIp ?: "-",
                    protocolSelection?.displayName
                )
            }
        } else {
            ConnectionState("-", 0f, "-", null)
        }

    private fun speedHistoryToChartData(
        trafficHistory: LiveData<List<TrafficUpdate>>
    ): LiveData<TrafficSpeedChartData> = map(trafficHistory) { updates ->
        if (updates.isEmpty()) {
            // The chart library freezes when adding data to a displayed, empty chart, so always
            // set some data.
            // https://github.com/PhilJay/MPAndroidChart/issues/2506
            TrafficSpeedChartData(listOf(Entry(0f, 0f)), listOf(Entry(0f, 0f)))
        } else {
            val lastTimestampMs = updates.last().timestampMs
            TrafficSpeedChartData(
                uploadKpbsHistory = updates.map { update ->
                    toEntry(update, lastTimestampMs) { it.uploadSpeed }
                },
                downloadKbpsHistory = updates.map { update ->
                    toEntry(update, lastTimestampMs) { it.downloadSpeed }
                }
            )
        }
    }

    private inline fun toEntry(update: TrafficUpdate, lastTimestampMs: Long, getter: (TrafficUpdate) -> Long) =
        Entry(
            (update.timestampMs - lastTimestampMs).toFloat() / MILLIS_IN_SECOND,
            getter(update).toFloat() / BYTES_IN_KBYTE
        )
}
