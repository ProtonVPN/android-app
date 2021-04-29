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
package com.protonvpn.android.models.vpn.wireguard

import android.os.Parcel
import android.os.Parcelable
import androidx.databinding.BaseObservable
import androidx.databinding.Observable
import androidx.databinding.ObservableList
import com.wireguard.config.Attribute
import com.wireguard.config.BadConfigException
import com.wireguard.config.Peer
import java.lang.ref.WeakReference
import java.util.ArrayList
import java.util.LinkedHashSet

class PeerProxy : BaseObservable {

    private val dnsRoutes: MutableList<String?> = ArrayList()
    private var allowedIpsState = AllowedIpsState.INVALID
    private var interfaceDnsListener: InterfaceDnsListener? = null
    private var peerListListener: PeerListListener? = null
    private var owner: ConfigProxy? = null
    private var totalPeers = 0

    var allowedIps: String = ""
        set(value) {
            field = value
            calculateAllowedIpsState()
        }

    var endpoint: String = ""

    var persistentKeepalive: String = ""

    var preSharedKey: String = ""

    var publicKey: String = ""

    val isAbleToExcludePrivateIps: Boolean
        get() = allowedIpsState == AllowedIpsState.CONTAINS_IPV4_PUBLIC_NETWORKS ||
                allowedIpsState == AllowedIpsState.CONTAINS_IPV4_WILDCARD

    val isExcludingPrivateIps: Boolean
        get() = allowedIpsState == AllowedIpsState.CONTAINS_IPV4_PUBLIC_NETWORKS

    private constructor(parcel: Parcel) {
        allowedIps = parcel.readString() ?: ""
        endpoint = parcel.readString() ?: ""
        persistentKeepalive = parcel.readString() ?: ""
        preSharedKey = parcel.readString() ?: ""
        publicKey = parcel.readString() ?: ""
    }

    constructor(other: Peer) {
        allowedIps = Attribute.join(other.allowedIps)
        endpoint = other.endpoint.map { it.toString() }.orElse("")
        persistentKeepalive = other.persistentKeepalive.map { it.toString() }.orElse("")
        preSharedKey = other.preSharedKey.map { it.toBase64() }.orElse("")
        publicKey = other.publicKey.toBase64()
    }

    constructor()

    fun bind(owner: ConfigProxy) {
        val interfaze: InterfaceProxy = owner.interfaceProxy
        val peers = owner.peers
        if (interfaceDnsListener == null) interfaceDnsListener = InterfaceDnsListener(this)
        interfaze.addOnPropertyChangedCallback(interfaceDnsListener!!)
        setInterfaceDns(interfaze.dnsServers)
        if (peerListListener == null) peerListListener = PeerListListener(this)
        peers.addOnListChangedCallback(peerListListener)
        setTotalPeers(peers.size)
        this.owner = owner
    }

    private fun calculateAllowedIpsState() {
        val newState: AllowedIpsState
        newState = if (totalPeers == 1) {
            // String comparison works because we only care if allowedIps is a superset of one of
            // the above sets of (valid) *networks*. We are not checking for a superset based on
            // the individual addresses in each set.
            val networkStrings: Collection<String> = getAllowedIpsSet()
            // If allowedIps contains both the wildcard and the public networks, then private
            // networks aren't excluded!
            when {
                networkStrings.containsAll(IPV4_WILDCARD) -> AllowedIpsState.CONTAINS_IPV4_WILDCARD
                networkStrings.containsAll(IPV4_PUBLIC_NETWORKS) -> AllowedIpsState.CONTAINS_IPV4_PUBLIC_NETWORKS
                else -> AllowedIpsState.OTHER
            }
        } else {
            AllowedIpsState.INVALID
        }
        if (newState != allowedIpsState) {
            allowedIpsState = newState
        }
    }

    private fun getAllowedIpsSet() = setOf(*Attribute.split(allowedIps))

    // Replace the first instance of the wildcard with the public network list, or vice versa.
    // DNS servers only need to handled specially when we're excluding private IPs.
    fun setExcludingPrivateIps(excludingPrivateIps: Boolean) {
        if (!isAbleToExcludePrivateIps || isExcludingPrivateIps == excludingPrivateIps) return
        val oldNetworks = if (excludingPrivateIps) IPV4_WILDCARD else IPV4_PUBLIC_NETWORKS
        val newNetworks = if (excludingPrivateIps) IPV4_PUBLIC_NETWORKS else IPV4_WILDCARD
        val input: Collection<String> = getAllowedIpsSet()
        val outputSize = input.size - oldNetworks.size + newNetworks.size
        val output: MutableCollection<String?> = LinkedHashSet(outputSize)
        var replaced = false
        // Replace the first instance of the wildcard with the public network list, or vice versa.
        for (network in input) {
            if (oldNetworks.contains(network)) {
                if (!replaced) {
                    for (replacement in newNetworks) if (!output.contains(replacement)) output.add(replacement)
                    replaced = true
                }
            } else if (!output.contains(network)) {
                output.add(network)
            }
        }
        // DNS servers only need to handled specially when we're excluding private IPs.
        if (excludingPrivateIps) output.addAll(dnsRoutes) else output.removeAll(dnsRoutes)
        allowedIps = Attribute.join(output)
        allowedIpsState = if (excludingPrivateIps)
            AllowedIpsState.CONTAINS_IPV4_PUBLIC_NETWORKS else AllowedIpsState.CONTAINS_IPV4_WILDCARD
    }

    @Throws(BadConfigException::class)
    fun resolve(): Peer {
        val builder = Peer.Builder()
        if (allowedIps.isNotEmpty()) builder.parseAllowedIPs(allowedIps)
        if (endpoint.isNotEmpty()) builder.parseEndpoint(endpoint)
        if (persistentKeepalive.isNotEmpty()) builder.parsePersistentKeepalive(persistentKeepalive)
        if (preSharedKey.isNotEmpty()) builder.parsePreSharedKey(preSharedKey)
        if (publicKey.isNotEmpty()) builder.parsePublicKey(publicKey)
        return builder.build()
    }

    private fun setInterfaceDns(dnsServers: CharSequence) {
        val newDnsRoutes = Attribute.split(dnsServers).filter { !it.contains(":") }.map { "$it/32" }
        if (allowedIpsState == AllowedIpsState.CONTAINS_IPV4_PUBLIC_NETWORKS) {
            val input = getAllowedIpsSet()
            // Yes, this is quadratic in the number of DNS servers, but most users have 1 or 2.
            val output =
                input.filter { !dnsRoutes.contains(it) || newDnsRoutes.contains(it) }.plus(newDnsRoutes)
                    .distinct()
            // None of the public networks are /32s, so this cannot change the AllowedIPs state.
            allowedIps = Attribute.join(output)
            //   notifyPropertyChanged(BR.allowedIps)
        }
        dnsRoutes.clear()
        dnsRoutes.addAll(newDnsRoutes)
    }

    private fun setTotalPeers(totalPeers: Int) {
        if (this.totalPeers == totalPeers) return
        this.totalPeers = totalPeers
        calculateAllowedIpsState()
    }

    private enum class AllowedIpsState { CONTAINS_IPV4_PUBLIC_NETWORKS, CONTAINS_IPV4_WILDCARD, INVALID, OTHER
    }

    private class InterfaceDnsListener constructor(peerProxy: PeerProxy) :
        Observable.OnPropertyChangedCallback() {

        private val weakPeerProxy: WeakReference<PeerProxy> = WeakReference(peerProxy)
        override fun onPropertyChanged(sender: Observable, propertyId: Int) {
            val peerProxy = weakPeerProxy.get()
            if (peerProxy == null) {
                sender.removeOnPropertyChangedCallback(this)
                return
            }
            // This shouldn't be possible, but try to avoid a ClassCastException anyway.
            if (sender !is InterfaceProxy) return

            peerProxy.setInterfaceDns(sender.dnsServers)
        }
    }

    private class PeerListListener(peerProxy: PeerProxy) :
        ObservableList.OnListChangedCallback<ObservableList<PeerProxy?>>() {

        private val weakPeerProxy: WeakReference<PeerProxy> = WeakReference(peerProxy)
        override fun onChanged(sender: ObservableList<PeerProxy?>) {
            val peerProxy = weakPeerProxy.get()
            if (peerProxy == null) {
                sender.removeOnListChangedCallback(this)
                return
            }
            peerProxy.setTotalPeers(sender.size)
        }

        override fun onItemRangeChanged(
            sender: ObservableList<PeerProxy?>,
            positionStart: Int,
            itemCount: Int
        ) {
            // Do nothing.
        }

        override fun onItemRangeInserted(
            sender: ObservableList<PeerProxy?>,
            positionStart: Int,
            itemCount: Int
        ) {
            onChanged(sender)
        }

        override fun onItemRangeMoved(
            sender: ObservableList<PeerProxy?>,
            fromPosition: Int,
            toPosition: Int,
            itemCount: Int
        ) {
            // Do nothing.
        }

        override fun onItemRangeRemoved(
            sender: ObservableList<PeerProxy?>,
            positionStart: Int,
            itemCount: Int
        ) {
            onChanged(sender)
        }
    }

    private class PeerProxyCreator : Parcelable.Creator<PeerProxy> {

        override fun createFromParcel(parcel: Parcel): PeerProxy = PeerProxy(parcel)

        override fun newArray(size: Int): Array<PeerProxy?> = arrayOfNulls(size)
    }

    companion object {

        @JvmField val CREATOR: Parcelable.Creator<PeerProxy> = PeerProxyCreator()
        private val IPV4_PUBLIC_NETWORKS = setOf(
            "0.0.0.0/5",
            "8.0.0.0/7",
            "11.0.0.0/8",
            "12.0.0.0/6",
            "16.0.0.0/4",
            "32.0.0.0/3",
            "64.0.0.0/2",
            "128.0.0.0/3",
            "160.0.0.0/5",
            "168.0.0.0/6",
            "172.0.0.0/12",
            "172.32.0.0/11",
            "172.64.0.0/10",
            "172.128.0.0/9",
            "173.0.0.0/8",
            "174.0.0.0/7",
            "176.0.0.0/4",
            "192.0.0.0/9",
            "192.128.0.0/11",
            "192.160.0.0/13",
            "192.169.0.0/16",
            "192.170.0.0/15",
            "192.172.0.0/14",
            "192.176.0.0/12",
            "192.192.0.0/10",
            "193.0.0.0/8",
            "194.0.0.0/7",
            "196.0.0.0/6",
            "200.0.0.0/5",
            "208.0.0.0/4"
        )
        private val IPV4_WILDCARD = setOf("0.0.0.0/0")
    }
}
