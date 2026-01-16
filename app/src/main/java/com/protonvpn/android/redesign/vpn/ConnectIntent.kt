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
import com.protonvpn.android.redesign.recents.data.SettingsOverrides
import java.util.EnumSet

sealed interface AnyConnectIntent {

    val profileId: Long?
    val features: Set<ServerFeature>
    val settingsOverrides: SettingsOverrides?
    val canBeExcluded: Boolean

    // GuestHole is special, it doesn't get saved to recents nor shown in the UI.
    data class GuestHole(
        val serverId: String,
    ) : AnyConnectIntent {
        override val profileId: Long? get() = null
        override val features: Set<ServerFeature> = EnumSet.noneOf(ServerFeature::class.java)
        override val settingsOverrides: SettingsOverrides? = null

        override val canBeExcluded: Boolean = false

    }
}

// Regular, user-facing connect intents.
sealed interface ConnectIntent : AnyConnectIntent {

    data class FastestInCountry(
        val country: CountryId,
        override val features: Set<ServerFeature>,
        override val profileId: Long? = null,
        override val settingsOverrides: SettingsOverrides? = null,
    ) : ConnectIntent {

        override val canBeExcluded: Boolean = country.isFastest

    }

    data class FastestInCity(
        val country: CountryId,
        val cityEn: String, // Not translated.
        override val features: Set<ServerFeature>,
        override val profileId: Long? = null,
        override val settingsOverrides: SettingsOverrides? = null,
    ) : ConnectIntent {

        override val canBeExcluded: Boolean = false

    }

    data class FastestInState(
        val country: CountryId,
        val stateEn: String, // Not translated.
        override val features: Set<ServerFeature>,
        override val profileId: Long? = null,
        override val settingsOverrides: SettingsOverrides? = null,
    ) : ConnectIntent {

        override val canBeExcluded: Boolean = false

    }

    data class SecureCore(
        val exitCountry: CountryId,
        val entryCountry: CountryId,
        override val profileId: Long? = null,
        override val settingsOverrides: SettingsOverrides? = null,
    ) : ConnectIntent {

        override val features: Set<ServerFeature> = EnumSet.noneOf(ServerFeature::class.java)

        override val canBeExcluded: Boolean = exitCountry.isFastest

    }

    data class Gateway(
        val gatewayName: String,
        val serverId: String?,
        override val profileId: Long? = null,
        override val settingsOverrides: SettingsOverrides? = null,
    ) : ConnectIntent {
        val fastest = serverId == null

        override val features: Set<ServerFeature> = EnumSet.noneOf(ServerFeature::class.java)

        override val canBeExcluded: Boolean = false

    }

    // Note: it's possible that we'll need more information about the server to be able to handle fallbacks if the
    // server is removed.
    data class Server(
        val serverId: String,
        val exitCountry: CountryId?, // To be used as a fallback when available and server is removed.
        override val features: Set<ServerFeature>,
        override val profileId: Long? = null,
        override val settingsOverrides: SettingsOverrides? = null,
    ) : ConnectIntent {

        override val canBeExcluded: Boolean = false

    }

    companion object {
        val Fastest = FastestInCountry(CountryId.fastest, EnumSet.noneOf(ServerFeature::class.java))
        val Default = Fastest

        fun fromServer(server: com.protonvpn.android.servers.Server, features: Set<ServerFeature>) = with (server) {
            Server(serverId, CountryId(exitCountry), features)
        }
    }
}
