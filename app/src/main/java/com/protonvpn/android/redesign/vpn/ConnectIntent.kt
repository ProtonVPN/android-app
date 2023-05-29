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

sealed interface ConnectIntent {

    val features: Set<ServerFeature>

    data class FastestInCountry(
        val country: CountryId,
        override val features: Set<ServerFeature>,
    ) : ConnectIntent

    // TODO: regions
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

    // Note: it's possible that we'll need more information about the server to be able to handle fallbacks if the
    // server is removed.
    data class Server(
        val serverId: String,
        override val features: Set<ServerFeature>,
    ) : ConnectIntent
}
