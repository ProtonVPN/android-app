/*
 * Copyright (c) 2025 Proton AG
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

package com.protonvpn.android.models.vpn.usecase

import android.content.Context
import androidx.annotation.VisibleForTesting
import com.protonvpn.android.logging.LogCategory
import com.protonvpn.android.logging.ProtonLogger
import com.protonvpn.android.settings.data.LocalUserSettings
import com.protonvpn.android.settings.data.SplitTunnelingMode
import com.protonvpn.android.utils.Constants
import dagger.Reusable
import dagger.hilt.android.qualifiers.ApplicationContext
import de.blinkt.openvpn.core.LOCAL_RANGES_IP_V4
import de.blinkt.openvpn.core.LOCAL_RANGES_IP_V6
import de.blinkt.openvpn.core.NetworkUtils
import inet.ipaddr.IPAddress
import inet.ipaddr.IPAddressSeqRange
import inet.ipaddr.IPAddressString
import inet.ipaddr.ipv4.IPv4Address
import inet.ipaddr.ipv4.IPv4AddressSeqRange
import inet.ipaddr.ipv6.IPv6Address
import inet.ipaddr.ipv6.IPv6AddressSeqRange
import javax.inject.Inject

fun interface ProvideLocalNetworks {
    operator fun invoke(ipV6Enabled: Boolean): List<IPAddress>
}

@Reusable
class ProvideLocalNetworksImpl @Inject constructor(
    @ApplicationContext private val appContext: Context,
): ProvideLocalNetworks {
    override fun invoke(ipV6Enabled: Boolean): List<IPAddress> =
        buildList {
            addAll(NetworkUtils.getLocalNetworks(appContext, false))
            if (ipV6Enabled)
                NetworkUtils.getLocalNetworks(appContext, true)
        }.map { it.toIPAddress() }
}

// Computes IPs for routing inside of the tunnel.
@Reusable
class ComputeAllowedIPs @Inject constructor(
    private val provideLocalNetworks: ProvideLocalNetworks,
) {
    operator fun invoke(
        userSettings: LocalUserSettings,
        alwaysAllowedIPs: List<IPAddress> = ALWAYS_ALLOWED_IPS
    ): List<IPAddress> {
        val ipV6Enabled = userSettings.ipV6Enabled
        val fullIPv6 = listOf(IPAddressString("::/0").address)
        val splitTunneling = userSettings.splitTunneling
        val includeOnly = with(splitTunneling) {
            isEnabled && mode == SplitTunnelingMode.INCLUDE_ONLY && includedIps.isNotEmpty()
        }

        val alwaysAllowedFilteredIPs = alwaysAllowedIPs.filter { ipV6Enabled || it.isIPv4 }.distinct()
        val allowedIPs = if (includeOnly) {
            val includeIPs = splitTunneling.includedIps
                .map { it.toIPAddress() }
                .filter { !it.isLoopback }

            // Note: allow LAN is ignored, mostly for consistency with OpenVPN where this behavior is implemented in
            // OpenVPNService. LAN is accessible in INCLUDE_ONLY mode anyway unless the user explicitly configures
            // LAN IPs to go via VPN - there should be no reason to do this.

            if (ipV6Enabled) {
                includeIPs
            } else {
                includeIPs.filter { it.isIPv4 } + fullIPv6
            }
        } else {
            val excludedIps = mutableListOf<IPAddress>()
            if (splitTunneling.isEnabled && splitTunneling.mode == SplitTunnelingMode.EXCLUDE_ONLY)
                excludedIps += splitTunneling.excludedIps.map { it.toIPAddress() }

            if (userSettings.lanConnections) {
                excludedIps += getLocalRanges(
                    ipV6Enabled = ipV6Enabled,
                    allowDirectConnections = userSettings.lanConnectionsAllowDirect
                ).apply {
                    ProtonLogger.logCustom(LogCategory.CONN, "Excluded local networks: $this")
                }
            }

            val allowedIPsV4 = excludeFrom(FULL_RANGE_IP_V4, excludedIps)
            val allowedIPsV6 =
                if (ipV6Enabled) excludeFrom(FULL_RANGE_IP_V6, excludedIps)
                else fullIPv6
            allowedIPsV4 + allowedIPsV6
        }
        val allRanges = (alwaysAllowedFilteredIPs + allowedIPs).map { it.toSequentialRange() }
        return allRanges.joinToIPList()
    }

    @VisibleForTesting
    fun getLocalRanges(ipV6Enabled: Boolean, allowDirectConnections: Boolean): List<IPAddress> =
        if (allowDirectConnections) {
            LOCAL_RANGES_IP_V4 + if (ipV6Enabled) LOCAL_RANGES_IP_V6 else emptyList()
        } else {
            provideLocalNetworks(ipV6Enabled)
        }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    fun excludeFrom(
        range: IPAddressSeqRange,
        excludedIps: List<IPAddress>,
    ): List<IPAddressSeqRange> {
        var ranges = removeIPsFromRanges(listOf(range), excludedIps)
        var allowedIPs = ranges.flatMap { it.spanWithPrefixBlocks().toList() }

        // Allowed IPs cannot include loopback IPs, otherwise VpnService.Builder.addRoute()
        // is going to throw an exception. To circumvent this, exclude any existing loopback IPs.
        val haveLoopbackV4 = allowedIPs.any { it.isIPv4 && it.isLoopback }
        val haveLoopbackV6 = allowedIPs.any { it.isIPv6 && it.isLoopback }
        if (haveLoopbackV4)
            ranges = removeIPsFromRanges(ranges, listOf("127.0.0.0/8".toIPAddress()))
        if (haveLoopbackV6)
            ranges = removeIPsFromRanges(ranges, listOf("::1/128".toIPAddress()))
        return ranges
    }
}

private val ALWAYS_ALLOWED_IPS = listOf(
    Constants.LOCAL_AGENT_IP.toIPAddress(),
    Constants.PROTON_DNS_LOCAL_IP.toIPAddress(),
    Constants.VPN_SERVER_IP.toIPAddress(),
    Constants.VPN_SERVER_IP_V6.toIPAddress(),
    Constants.PROTON_DNS_LOCAL_IP_V6.toIPAddress(),
)

val FULL_RANGE_IP_V4 = IPv4AddressSeqRange(
    IPv4Address(ByteArray(4)),
    IPv4Address(ByteArray(4) { 0xff.toByte() }),
)

val FULL_RANGE_IP_V6 = IPv6AddressSeqRange(
    IPv6Address(ByteArray(16)),
    IPv6Address(ByteArray(16) { 0xff.toByte() }),
)

fun String.toIPAddress(): IPAddress = IPAddressString(this).address

// Optimize ranges by joining and converts to list of IPs.
fun List<IPAddressSeqRange>.joinToIPList() =
    IPAddressSeqRange
        .join(*this.toTypedArray())
        .flatMap { it.spanWithPrefixBlocks().toList() }

@VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
fun removeIPsFromRanges(
    ranges: List<IPAddressSeqRange>,
    ips: List<IPAddress>
): List<IPAddressSeqRange> {
    val toRemove = ips.map { it.toPrefixBlock().toSequentialRange() }
    return toRemove.fold(ranges) { currentRanges, ip ->
        currentRanges.flatMap { range ->
            if (range.overlaps(ip))
                range.subtract(ip).toList()
            else
                listOf(range)
        }
    }
}