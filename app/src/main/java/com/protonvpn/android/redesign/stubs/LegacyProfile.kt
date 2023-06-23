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
import com.protonvpn.android.redesign.CountryId
import com.protonvpn.android.redesign.vpn.ConnectIntent
import com.protonvpn.android.redesign.vpn.ServerFeature
import com.protonvpn.android.settings.data.EffectiveCurrentUserSettingsCached
import com.protonvpn.android.settings.data.LocalUserSettings
import com.protonvpn.android.utils.ServerManager
import java.util.EnumSet

// TODO: when implementing profiles to recents migration move this code there. And write unit tests!
@Deprecated("Don't use profiles.")
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
