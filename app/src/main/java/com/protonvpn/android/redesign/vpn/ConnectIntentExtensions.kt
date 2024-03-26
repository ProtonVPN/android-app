/*
 * Copyright (c) 2024. Proton AG
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

import com.protonvpn.android.models.vpn.Server
import com.protonvpn.android.redesign.CountryId

fun Server?.isCompatibleWith(intent: ConnectIntent, matchFastest: Boolean): Boolean {
    if (this == null) return false

    fun CountryId.matches(serverCountryCode: String) = (matchFastest && isFastest) || countryCode == serverCountryCode

    return satisfiesFeatures(intent.features) && when(intent) {
        is ConnectIntent.FastestInCountry ->
            intent.country.matches(exitCountry) && !isSecureCoreServer
        is ConnectIntent.FastestInCity ->
            intent.country.matches(exitCountry) && intent.cityEn == city && !isSecureCoreServer
        is ConnectIntent.FastestInState ->
            intent.country.matches(exitCountry) && intent.stateEn == state && !isSecureCoreServer
        is ConnectIntent.SecureCore ->
            intent.entryCountry.matches(entryCountry) && intent.exitCountry.matches(exitCountry) && isSecureCoreServer
        is ConnectIntent.Gateway ->
            intent.gatewayName == gatewayName && (intent.serverId == null || intent.serverId == serverId)
        is ConnectIntent.Server -> intent.serverId == serverId && gatewayName.isNullOrEmpty() && !isSecureCoreServer
    }
}
