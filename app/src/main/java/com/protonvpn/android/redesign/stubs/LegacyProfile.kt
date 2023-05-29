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

package com.protonvpn.android.redesign.stubs

import com.protonvpn.android.models.profiles.Profile
import com.protonvpn.android.models.profiles.ServerWrapper
import com.protonvpn.android.redesign.CountryId
import com.protonvpn.android.redesign.vpn.ConnectIntent
import com.protonvpn.android.redesign.vpn.ServerFeature
import com.protonvpn.android.settings.data.EffectiveCurrentUserSettingsCached
import com.protonvpn.android.settings.data.LocalUserSettings
import com.protonvpn.android.utils.ServerManager
import java.util.EnumSet

fun Profile.toConnectIntent(
    serverManager: ServerManager,
    userSettings: LocalUserSettings
): ConnectIntent {
    // Profiles don't have this info.
    val serverFeatures = EnumSet.noneOf(ServerFeature::class.java)
    val isEffectivelySecureCore = isSecureCore ?: userSettings.secureCore
    return when {
        isPreBakedFastest -> if (isEffectivelySecureCore) {
            ConnectIntent.SecureCore(CountryId.fastest, CountryId.fastest)
        } else {
            ConnectIntent.FastestInCountry(CountryId.fastest, serverFeatures)
        }
        isEffectivelySecureCore -> {
            val entryCountry = when {
                wrapper.isFastestInCountry -> CountryId.fastest
                wrapper.serverId != null ->
                    CountryId(requireNotNull(serverManager.getServerById(wrapper.serverId)).entryCountry)
                else -> CountryId.fastest
            }
            ConnectIntent.SecureCore(CountryId(country), entryCountry)
        }
        wrapper.isFastestInCountry -> ConnectIntent.FastestInCountry(CountryId(wrapper.country), serverFeatures)
        !directServerId.isNullOrBlank() -> ConnectIntent.Server(directServerId!!, serverFeatures)
        else -> ConnectIntent.FastestInCountry(CountryId.fastest, serverFeatures)
    }
}

fun ConnectIntent.toProfile(
    serverManager: ServerManager
): Profile = when (this) {
    is ConnectIntent.FastestInCountry ->
        if (country.isFastest) {
            Profile("", null, ServerWrapper.makePreBakedFastest(), null, false)
        } else {
            Profile("", null, ServerWrapper.makeFastestForCountry(country.countryCode), null, false)
        }
    is ConnectIntent.FastestInCity -> {
        TODO("No way to do this with profiles")
    }
    is ConnectIntent.SecureCore ->
        if (exitCountry.isFastest) {
            Profile("", null, ServerWrapper.makePreBakedFastest(), null, true)
        } else if (entryCountry.isFastest) {
            Profile("", null, ServerWrapper.makeFastestForCountry(exitCountry.countryCode), null, true)
        } else {
            val server = serverManager
                .getVpnExitCountry(exitCountry.countryCode, true)
                ?.serverList
                ?.find { server -> server.entryCountry == entryCountry.countryCode }
            Profile("", null, ServerWrapper.makeWithServer(requireNotNull(server)), null, true)
        }
    is ConnectIntent.Server ->
        Profile("", null, ServerWrapper.makeWithServer(serverManager.getServerById(serverId)!!), null, false)
}
