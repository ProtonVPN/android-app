/*
 * Copyright (c) 2017 Proton Technologies AG
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
package com.protonvpn.android.models.profiles

import com.google.gson.annotations.SerializedName
import com.protonvpn.android.models.vpn.Server
import java.io.Serializable

@SuppressWarnings("DataClassShouldBeImmutable")
data class ServerWrapper(
    val type: ProfileType,
    val country: String,
    val serverId: String?
) : Serializable {

    enum class ProfileType {
        FASTEST, RANDOM, RANDOM_IN_COUNTRY, FASTEST_IN_COUNTRY, DIRECT
    }

    @SerializedName("secureCoreCountry") val migrateSecureCoreCountry = false

    override fun toString() =
        "type: $type country: $country serverId: $serverId"

    val isPreBakedFastest get() = type == ProfileType.FASTEST
    val isPreBakedRandom get() = type == ProfileType.RANDOM
    val isFastestInCountry get() = type == ProfileType.FASTEST_IN_COUNTRY
    val isRandomInCountry get() = type == ProfileType.RANDOM_IN_COUNTRY
    val isPreBakedProfile get() = type == ProfileType.FASTEST || type == ProfileType.RANDOM

    companion object {

        @JvmStatic
        fun makePreBakedFastest() =
            ServerWrapper(ProfileType.FASTEST, "", "")

        @JvmStatic
        fun makePreBakedRandom() =
            ServerWrapper(ProfileType.RANDOM, "", "")

        @JvmStatic
        fun makeWithServer(server: Server) =
            ServerWrapper(ProfileType.DIRECT, server.exitCountry, server.serverId)

        @JvmStatic
        fun makeFastestForCountry(country: String) =
            ServerWrapper(ProfileType.FASTEST_IN_COUNTRY, country, "")

        @JvmStatic
        fun makeRandomForCountry(country: String) =
            ServerWrapper(ProfileType.RANDOM_IN_COUNTRY, country, "")
    }
}
