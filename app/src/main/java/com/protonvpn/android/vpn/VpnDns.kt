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

import com.protonvpn.android.logging.AppDNS
import com.protonvpn.android.logging.ProtonLogger
import com.protonvpn.android.utils.Constants
import com.protonvpn.android.utils.runCatchingCheckedExceptions
import dagger.Reusable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import okhttp3.Dns
import org.xbill.DNS.Lookup
import org.xbill.DNS.SimpleResolver
import org.xbill.DNS.Type
import java.net.InetAddress
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

//TODO: add support in core
@Reusable
class VpnDns @Inject constructor(
    mainScope: CoroutineScope,
    vpnStateMonitor: VpnStateMonitor,
    private val connectivityMonitor: ConnectivityMonitor,
) : Dns {

    val inTunnel =
        vpnStateMonitor.internalVpnProtocolState.map { it != VpnState.Disabled }
            .stateIn(mainScope, SharingStarted.Eagerly, false)

    override fun lookup(hostname: String): List<InetAddress> = when {
        !inTunnel.value -> Dns.SYSTEM.lookup(hostname)
        connectivityMonitor.isPrivateDnsActive.value == true ->
            resolveWithProtonDNS(hostname) ?: Dns.SYSTEM.lookup(hostname)
        else ->
            Dns.SYSTEM.lookup(hostname).takeIf { it.isNotEmpty() }
                ?: resolveWithProtonDNS(hostname)
                ?: emptyList()
    }

    private fun resolveWithProtonDNS(hostname: String): List<InetAddress>? =
        resolveHostname(hostname, Constants.PROTON_DNS_LOCAL_IP)
}

private fun resolveHostname(hostname: String, dnsServer: String): List<InetAddress>? = {
    val resolver = SimpleResolver(dnsServer)
    resolver.timeout = 2.seconds.toJavaDuration()
    val lookup = Lookup(hostname, Type.A)
    lookup.setResolver(resolver)
    val records = lookup.run()
    records?.map { (it as org.xbill.DNS.ARecord).address }
}.runCatchingCheckedExceptions {
    ProtonLogger.log(AppDNS, "Failed to resolve $hostname (${it.message})")
    null
}