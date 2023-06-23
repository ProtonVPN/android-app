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

package com.protonvpn.android.redesign.recents.data

import com.protonvpn.android.redesign.CountryId
import com.protonvpn.android.redesign.vpn.AnyConnectIntent
import com.protonvpn.android.redesign.vpn.ConnectIntent
import com.protonvpn.android.redesign.vpn.ServerFeature
import me.proton.core.util.kotlin.takeIfNotBlank

// TODO: fastest in state/region
enum class ConnectIntentType {
    FASTEST,
    FASTEST_IN_CITY,
    SECURE_CORE,
    SPECIFIC_SERVER,
    GUEST_HOLE
}

data class ConnectIntentData(
    val connectIntentType: ConnectIntentType,
    val exitCountry: String?,
    val entryCountry: String?,
    val city: String?,
    val serverId: String?,
    val features: Set<ServerFeature>
) : java.io.Serializable

fun ConnectIntentData.toConnectIntent(): ConnectIntent =
    when (connectIntentType) {
        ConnectIntentType.FASTEST ->
            ConnectIntent.FastestInCountry(
                country = exitCountry.toCountryId(),
                features = features
            )

        ConnectIntentType.FASTEST_IN_CITY ->
            ConnectIntent.FastestInCity(
                country = exitCountry.toCountryId(),
                cityEn = requireNotNull(city),
                features = features
            )

        ConnectIntentType.SECURE_CORE ->
            ConnectIntent.SecureCore(
                exitCountry = exitCountry.toCountryId(),
                entryCountry = entryCountry.toCountryId(),
            )

        ConnectIntentType.SPECIFIC_SERVER ->
            ConnectIntent.Server(
                serverId = requireNotNull(serverId),
                features = features
            )
        ConnectIntentType.GUEST_HOLE -> throw IllegalArgumentException("GUEST_HOLE is not a regular ConnectIntent type")
    }

fun ConnectIntentData.toAnyConnectIntent(): AnyConnectIntent = when(connectIntentType) {
    ConnectIntentType.GUEST_HOLE -> AnyConnectIntent.GuestHole(requireNotNull(serverId))
    else -> this.toConnectIntent()
}

fun ConnectIntent.toData(): ConnectIntentData =
    when(this) {
        is ConnectIntent.FastestInCountry ->
            ConnectIntentData(
                connectIntentType = ConnectIntentType.FASTEST,
                exitCountry = country.toDataString(),
                entryCountry = null,
                city = null,
                serverId = null,
                features = features
            )
        is ConnectIntent.FastestInCity ->
            ConnectIntentData(
                connectIntentType = ConnectIntentType.FASTEST_IN_CITY,
                exitCountry = country.toDataString(),
                entryCountry = null,
                city = cityEn,
                serverId = null,
                features = features
            )
        is ConnectIntent.SecureCore ->
            ConnectIntentData(
                connectIntentType = ConnectIntentType.SECURE_CORE,
                exitCountry = exitCountry.toDataString(),
                entryCountry = entryCountry.toDataString(),
                city = null,
                serverId = null,
                features = features
            )
        is ConnectIntent.Server ->
            ConnectIntentData(
                connectIntentType = ConnectIntentType.SPECIFIC_SERVER,
                exitCountry = null,
                entryCountry = null,
                city = null,
                serverId = serverId,
                features = features
            )
    }

fun AnyConnectIntent.toData() = when (this) {
    is AnyConnectIntent.GuestHole -> ConnectIntentData(
        connectIntentType = ConnectIntentType.GUEST_HOLE,
        exitCountry = null,
        entryCountry = null,
        city = null,
        serverId = serverId,
        features = emptySet()
    )
    is ConnectIntent -> this.toData()
}

private fun CountryId.toDataString(): String? = countryCode.takeIfNotBlank()
private fun String?.toCountryId(): CountryId = if (this == null) CountryId.fastest else CountryId(this)
