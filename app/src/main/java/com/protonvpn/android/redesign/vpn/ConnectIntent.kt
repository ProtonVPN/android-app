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

import com.protonvpn.android.redesign.CountryId
import java.util.EnumSet

sealed interface AnyConnectIntent {

    val features: Set<ServerFeature>

    // GuestHole is special, it doesn't get saved to recents nor shown in the UI.
    data class GuestHole(
        val serverId: String
    ) : AnyConnectIntent {
        override val features: Set<ServerFeature> = EnumSet.noneOf(ServerFeature::class.java)
    }
}

// Regular, user-facing connect intents.
sealed interface ConnectIntent : AnyConnectIntent {

    data class FastestInCountry(
        val country: CountryId,
        override val features: Set<ServerFeature>,
    ) : ConnectIntent

    // TODO: regions (VPNAND-1329)
    data class FastestInCity(
        val country: CountryId,
        val cityEn: String, // Not translated.
        override val features: Set<ServerFeature>,
    ) : ConnectIntent

    data class SecureCore(
        val exitCountry: CountryId,
        val entryCountry: CountryId,
    ) : ConnectIntent {
        override val features: Set<ServerFeature> = EnumSet.noneOf(ServerFeature::class.java)
    }

    data class Gateway(
        val gatewayName: String,
        val serverId: String?,
    ) : ConnectIntent {
        val fastest = serverId == null

        override val features: Set<ServerFeature> = EnumSet.noneOf(ServerFeature::class.java)
    }

    // Note: it's possible that we'll need more information about the server to be able to handle fallbacks if the
    // server is removed.
    data class Server(
        val serverId: String,
        override val features: Set<ServerFeature>,
    ) : ConnectIntent

    companion object {
        val Fastest = FastestInCountry(CountryId.fastest, EnumSet.noneOf(ServerFeature::class.java))
        val Default = Fastest
    }
}
