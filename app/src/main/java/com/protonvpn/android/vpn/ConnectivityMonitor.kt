/*
 * Copyright (c) 2021 Proton Technologies AG
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
package com.protonvpn.android.vpn

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL
import android.net.NetworkCapabilities.NET_CAPABILITY_DUN
import android.net.NetworkCapabilities.NET_CAPABILITY_FOREGROUND
import android.net.NetworkCapabilities.NET_CAPABILITY_FOTA
import android.net.NetworkCapabilities.NET_CAPABILITY_IA
import android.net.NetworkCapabilities.NET_CAPABILITY_IMS
import android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET
import android.net.NetworkCapabilities.NET_CAPABILITY_MCX
import android.net.NetworkCapabilities.NET_CAPABILITY_MMS
import android.net.NetworkCapabilities.NET_CAPABILITY_NOT_CONGESTED
import android.net.NetworkCapabilities.NET_CAPABILITY_NOT_METERED
import android.net.NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING
import android.net.NetworkCapabilities.NET_CAPABILITY_NOT_SUSPENDED
import android.net.NetworkCapabilities.NET_CAPABILITY_NOT_VPN
import android.net.NetworkCapabilities.NET_CAPABILITY_SUPL
import android.net.NetworkCapabilities.NET_CAPABILITY_TEMPORARILY_NOT_METERED
import android.net.NetworkCapabilities.NET_CAPABILITY_TRUSTED
import android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED
import android.net.NetworkCapabilities.NET_CAPABILITY_WIFI_P2P
import android.net.NetworkCapabilities.NET_CAPABILITY_XCAP
import android.os.Build
import androidx.annotation.RequiresApi
import com.protonvpn.android.utils.AndroidUtils.registerBroadcastReceiver
import com.protonvpn.android.utils.ProtonLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import javax.inject.Singleton

private const val NOT_VPN = "NOT_VPN"

@Singleton
class ConnectivityMonitor(
    coroutineScope: CoroutineScope,
    context: Context
) {

    private var currentCapabilities: Map<String, Boolean> = LinkedHashMap()

    val networkCapabilitiesFlow = MutableSharedFlow<Map<String, Boolean>>()

    // This means current connection goes through VPN tunnel. It doesn't mean connection is fully functional (we can
    // be hard-jailed for example) - for that use VpnStateMonitor
    val vpnActive get() = currentCapabilities[NOT_VPN] == false

    private val capabilitiesConstantMap = mutableMapOf(
        "MMS" to NET_CAPABILITY_MMS,
        "SUPL" to NET_CAPABILITY_SUPL,
        "DUN" to NET_CAPABILITY_DUN,
        "FOTA" to NET_CAPABILITY_FOTA,
        "IMS" to NET_CAPABILITY_IMS,
        "WIFI_P2P" to NET_CAPABILITY_WIFI_P2P,
        "IA" to NET_CAPABILITY_IA,
        "XCAP" to NET_CAPABILITY_XCAP,
        "NOT_METERED" to NET_CAPABILITY_NOT_METERED,
        "INTERNET" to NET_CAPABILITY_INTERNET,
        NOT_VPN to NET_CAPABILITY_NOT_VPN,
        "TRUSTED" to NET_CAPABILITY_TRUSTED,
        "TEMP NOT METERED" to NET_CAPABILITY_TEMPORARILY_NOT_METERED,
        "NOT SUSPENDED" to NET_CAPABILITY_MCX,
    ).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            put("VALIDATED", NET_CAPABILITY_VALIDATED)
            put("CAPTIVE PORTAL", NET_CAPABILITY_CAPTIVE_PORTAL)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            put("NOT ROAMING", NET_CAPABILITY_NOT_ROAMING)
            put("TRUSTED", NET_CAPABILITY_FOREGROUND)
            put("NOT CONGESTED", NET_CAPABILITY_NOT_CONGESTED)
            put("NOT SUSPENDED", NET_CAPABILITY_NOT_SUSPENDED)
        }
    } as Map<String, Int>

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {

        @RequiresApi(Build.VERSION_CODES.N)
        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            val diffMap = capabilitiesConstantMap.mapValues {
                networkCapabilities.hasCapability(it.value)
            }
            if (currentCapabilities != diffMap) {
                coroutineScope.launch {
                    networkCapabilitiesFlow.emit(diffMap)
                }
                currentCapabilities = diffMap
            }
        }

        override fun onLosing(network: Network, maxMsToLive: Int) {
            ProtonLogger.log("Loosing network ($maxMsToLive)")
        }

        override fun onUnavailable() {
            ProtonLogger.log("Network unavailable")
        }

        override fun onAvailable(network: Network) {
            ProtonLogger.log("Network available")
        }

        override fun onLost(network: Network) {
            ProtonLogger.log("Network lost")
        }
    }

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            cm.registerDefaultNetworkCallback(networkCallback)
        }
        context.registerBroadcastReceiver(IntentFilter(Intent.ACTION_AIRPLANE_MODE_CHANGED)) {
            ProtonLogger.log("Airplane mode: " + it?.getBooleanExtra("state", false))
        }
    }

}
