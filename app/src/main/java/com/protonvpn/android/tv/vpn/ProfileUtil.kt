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

package com.protonvpn.android.tv.vpn

import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.models.profiles.Profile
import com.protonvpn.android.models.profiles.ServerWrapper
import com.protonvpn.android.redesign.CountryId
import com.protonvpn.android.redesign.vpn.ConnectIntent
import com.protonvpn.android.utils.CountryTools
import com.protonvpn.android.utils.DebugUtils
import com.protonvpn.android.utils.ServerManager
import com.protonvpn.android.vpn.ProtocolSelection
import me.proton.core.util.kotlin.takeIfNotBlank

// Note: we should move away from using Profiles for TV recents.
fun createProfileForCountry(countryCode: String): Profile =
    Profile(
        CountryTools.getFullName(countryCode),
        null,
        ServerWrapper.makeFastestForCountry(countryCode),
        null,
        null
    )

fun getConnectCountry(
    serverManager: ServerManager,
    currentUser: CurrentUser,
    protocol: ProtocolSelection,
    profile: Profile
): String {
    DebugUtils.debugAssert("Random profile not supported in TV") {
        profile.wrapper.type != ServerWrapper.ProfileType.RANDOM
    }
    return profile.country.takeIfNotBlank()
        ?: serverManager.getServerForProfile(profile, currentUser.vpnUserCached(), protocol)?.exitCountry
        ?: ""
}

fun createIntentForDefaultProfile(
    serverManager: ServerManager,
    currentUser: CurrentUser,
    protocol: ProtocolSelection,
    profile: Profile
): ConnectIntent {
    val countryCode = getConnectCountry(serverManager, currentUser, protocol, profile)
    val countryId = if (countryCode.isNotEmpty()) CountryId(countryCode) else CountryId.fastest
    return ConnectIntent.FastestInCountry(countryId, emptySet())
}
