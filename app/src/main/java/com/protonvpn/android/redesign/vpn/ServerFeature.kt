/*
 * Copyright (c) 2023. Proton AG
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

package com.protonvpn.android.redesign.vpn

import com.protonvpn.android.models.vpn.SERVER_FEATURE_P2P
import com.protonvpn.android.models.vpn.SERVER_FEATURE_TOR
import com.protonvpn.android.models.vpn.Server
import com.protonvpn.android.utils.hasFlag
import java.util.EnumSet

enum class ServerFeature(val flag: Int) {
    P2P(SERVER_FEATURE_P2P),
    Tor(SERVER_FEATURE_TOR);

    companion object {
        fun fromServer(server: Server): Set<ServerFeature> = EnumSet.noneOf(ServerFeature::class.java).apply {
            if (server.isP2pServer) add(P2P)
            if (server.isTor) add(Tor)
        }
    }
}

fun Server.satisfiesFeatures(requiredFeatures: Set<ServerFeature>): Boolean =
    requiredFeatures.all { required -> features.hasFlag(required.flag) }
