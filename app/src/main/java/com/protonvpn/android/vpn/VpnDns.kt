/*
 * Copyright (c) 2024 Proton AG
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

import androidx.annotation.VisibleForTesting
import com.protonvpn.android.concurrency.VpnDispatcherProvider
import com.protonvpn.android.logging.AppDNS
import com.protonvpn.android.logging.ProtonLogger
import com.protonvpn.android.utils.Constants
import com.protonvpn.android.utils.runCatchingCheckedExceptions
import dagger.Reusable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.selects.select
import okhttp3.Dns
import org.xbill.DNS.Lookup
import org.xbill.DNS.SimpleResolver
import org.xbill.DNS.Type
import java.io.IOException
import java.net.InetAddress
import java.net.UnknownHostException
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

const val SYSTEM_DNS_HEAD_START = 20L

//TODO: add support in core
@Reusable
class VpnDns(
    private val mainScope: CoroutineScope,
    private val inTunnel: StateFlow<Boolean>,
    private val dispatcherProvider: VpnDispatcherProvider,
    private val systemResolver: suspend (String) -> List<InetAddress>,
    private val protonResolver: suspend (String) -> List<InetAddress>,
) : Dns {

    @Inject
    constructor(
        mainScope: CoroutineScope,
        vpnStateMonitor: VpnStateMonitor,
        dispatcherProvider: VpnDispatcherProvider,
    ) : this(
        mainScope = mainScope,
        inTunnel = vpnStateMonitor.internalVpnProtocolState.map { it != VpnState.Disabled }
            .stateIn(mainScope, SharingStarted.Eagerly, false),
        dispatcherProvider = dispatcherProvider,
        systemResolver = ::systemResolver,
        protonResolver = ::protonResolver,
    )

    @Throws(UnknownHostException::class)
    override fun lookup(hostname: String): List<InetAddress> = runBlocking {
        lookupSuspend(hostname)
    }

    @VisibleForTesting
    suspend fun lookupSuspend(hostname: String): List<InetAddress> = coroutineScope {
        if (!inTunnel.value) {
            systemResolver(hostname)
        } else {
            // When connected with VPN protocol but not authenticated, system's private DNS
            // might not be reachable. To resolve our API use proton VPN server's DNS in parallel.
            select {
                async(mainScope.coroutineContext + dispatcherProvider.Io) {
                    // Give system DNS a small head start, so if it's using cached value we don't
                    // start Proton DNS unnecessarily.
                    delay(SYSTEM_DNS_HEAD_START)
                    protonResolver(hostname)
                }.onAwait { it }
                async(mainScope.coroutineContext + dispatcherProvider.Io) {
                    systemResolver(hostname)
                }.onAwait { it }
            }
        }.also {
            if (it.isEmpty())
                throw UnknownHostException("Unable to resolve host \"$hostname\". Please check your connection.")
        }
    }
}

private fun systemResolver(hostname: String): List<InetAddress> =
    try {
        Dns.SYSTEM.lookup(hostname)
    } catch (e: IOException) {
        ProtonLogger.log(AppDNS, "System DNS failed to resolve $hostname (${e.message})")
        emptyList()
    }

private fun protonResolver(hostname: String): List<InetAddress> =
    resolveHostname(hostname, Constants.PROTON_DNS_LOCAL_IP)

private fun resolveHostname(hostname: String, dnsServer: String): List<InetAddress> = {
    val resolver = SimpleResolver(dnsServer)
    resolver.timeout = 2.seconds.toJavaDuration()
    val lookup = Lookup(hostname, Type.A)
    lookup.setResolver(resolver)
    val records = lookup.run()
    records?.mapNotNull { (it as? org.xbill.DNS.ARecord)?.address }
}.runCatchingCheckedExceptions {
    ProtonLogger.log(AppDNS, "Proton DNS failed to resolve $hostname (${it.message})")
    null
} ?: emptyList()
