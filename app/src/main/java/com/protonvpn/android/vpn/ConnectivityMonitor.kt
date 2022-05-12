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
import android.net.NetworkCapabilities.TRANSPORT_BLUETOOTH
import android.net.NetworkCapabilities.TRANSPORT_CELLULAR
import android.net.NetworkCapabilities.TRANSPORT_ETHERNET
import android.net.NetworkCapabilities.TRANSPORT_LOWPAN
import android.net.NetworkCapabilities.TRANSPORT_USB
import android.net.NetworkCapabilities.TRANSPORT_VPN
import android.net.NetworkCapabilities.TRANSPORT_WIFI
import android.net.NetworkCapabilities.TRANSPORT_WIFI_AWARE
import android.os.Build
import androidx.annotation.RequiresApi
import com.protonvpn.android.logging.LogCategory
import com.protonvpn.android.logging.LogLevel
import com.protonvpn.android.logging.NetworkChanged
import com.protonvpn.android.logging.NetworkUnavailable
import com.protonvpn.android.logging.ProtonLogger
import com.protonvpn.android.utils.AndroidUtils.registerBroadcastReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import javax.inject.Singleton

private const val NOT_VPN = "NOT_VPN"

@Singleton
class ConnectivityMonitor(
    mainScope: CoroutineScope,
    context: Context
) {
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private var currentCapabilities: Map<String, Boolean> = LinkedHashMap()
    private var currentTransports: Set<String> = emptySet()

    val networkCapabilitiesFlow = MutableSharedFlow<Map<String, Boolean>>()

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

    private val transportConstantsMap: Map<String, Int> = mutableMapOf(
        "Bluetooth" to TRANSPORT_BLUETOOTH,
        "Cellular" to TRANSPORT_CELLULAR,
        "Ethernet" to TRANSPORT_ETHERNET,
        "VPN" to TRANSPORT_VPN,
        "WiFi" to TRANSPORT_WIFI,
    ).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            put("WiFi-Aware", TRANSPORT_WIFI_AWARE)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            put("Lowpan", TRANSPORT_LOWPAN)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            put("USB", TRANSPORT_USB)
        }
    }

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {

        @RequiresApi(Build.VERSION_CODES.N)
        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            val newCapabilities = capabilitiesConstantMap.mapValues {
                networkCapabilities.hasCapability(it.value)
            }
            val newTransports = getTransports(networkCapabilities)
            val capabilitiesChanged = currentCapabilities != newCapabilities
            if (currentTransports != newTransports || capabilitiesChanged) {
                ProtonLogger.log(
                    NetworkChanged,
                    "default network: $network; transports: ${newTransports.joinToString(", ")}; " +
                        "capabilities: $newCapabilities"
                )
                currentTransports = newTransports
            }
            if (capabilitiesChanged) {
                mainScope.launch {
                    networkCapabilitiesFlow.emit(newCapabilities)
                }
                currentCapabilities = newCapabilities
            }
        }

        override fun onLosing(network: Network, maxMsToLive: Int) {
            ProtonLogger.logCustom(LogCategory.NETWORK, "NetworkCallback: losing network ($maxMsToLive)")
        }

        override fun onUnavailable() {
            ProtonLogger.log(NetworkUnavailable)
        }

        override fun onAvailable(network: Network) {
            logNetworkEvent("network available", network)
        }

        override fun onLost(network: Network) {
            logNetworkEvent("network lost", network)
            // onUnavailable is not being called when there no longer is a default network
            // (possibly a bug: https://issuetracker.google.com/issues/144891976 )
            // Check if there is an active network to log NetNetworkUnavailable.
            if (connectivityManager.activeNetwork == null) {
                ProtonLogger.log(NetworkUnavailable)
            }
        }

        private fun logNetworkEvent(event: String, network: Network) {
            val transports = connectivityManager.getNetworkCapabilities(network)?.let {
                getTransports(it).joinToString(", ")
            }.orEmpty()
            ProtonLogger.log(NetworkChanged, "$event: $network $transports")
        }
    }

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            mainScope.launch {
                registerNetworkCallbacksWithRetry()
            }
        }
        context.registerBroadcastReceiver(IntentFilter(Intent.ACTION_AIRPLANE_MODE_CHANGED)) {
            ProtonLogger.logCustom(
                LogCategory.NETWORK,
                "Airplane mode: " + it?.getBooleanExtra("state", false)
            )
        }
    }

    fun getCurrentStateForLog(): String =
        "transports: ${currentTransports.joinToString(", ")} capabilities: $currentCapabilities"

    private fun getTransports(networkCapabilities: NetworkCapabilities): Set<String> =
        transportConstantsMap.entries.mapNotNullTo(mutableSetOf()) {
            if (networkCapabilities.hasTransport(it.value)) it.key else null
        }

    @RequiresApi(Build.VERSION_CODES.N)
    private suspend fun registerNetworkCallbacksWithRetry() {
        try {
            connectivityManager.registerDefaultNetworkCallback(networkCallback)
        } catch (e: SecurityException) {
            ProtonLogger.logCustom(LogLevel.ERROR, LogCategory.APP, "SecurityException from ConnectivityManager: $e")
            // Android 11 bug: https://issuetracker.google.com/issues/175055271
            if (e.message?.startsWith("Package android does not belong to") == true) {
                delay(1000)
                connectivityManager.registerDefaultNetworkCallback(networkCallback)
            } else {
                throw e
            }
        }
    }
}
