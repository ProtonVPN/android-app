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

import androidx.databinding.ObservableArrayList
import androidx.databinding.ObservableList
import com.wireguard.config.BadConfigException
import com.wireguard.config.Config
import com.wireguard.config.Peer
import java.util.ArrayList

class ConfigProxy {

    val interfaceProxy: InterfaceProxy
    val peers: ObservableList<PeerProxy> = ObservableArrayList()

    constructor(other: Config) {
        interfaceProxy = InterfaceProxy(other.getInterface())
        other.peers.forEach {
            val proxy = PeerProxy(it)
            peers.add(proxy)
            proxy.bind(this)
        }
    }

    constructor() {
        interfaceProxy = InterfaceProxy()
    }

    fun addPeer(): PeerProxy {
        val proxy = PeerProxy()
        peers.add(proxy)
        proxy.bind(this)
        return proxy
    }

    @Throws(BadConfigException::class)
    fun resolve(): Config {
        val resolvedPeers: MutableCollection<Peer> = ArrayList()
        peers.forEach { resolvedPeers.add(it.resolve()) }
        return Config.Builder().setInterface(interfaceProxy.resolve()).addPeers(resolvedPeers).build()
    }

}
