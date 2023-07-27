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

package com.protonvpn.android.components

import com.protonvpn.android.models.vpn.SERVER_FEATURE_P2P
import com.protonvpn.android.models.vpn.SERVER_FEATURE_STREAMING
import com.protonvpn.android.models.vpn.SERVER_FEATURE_TOR
import com.protonvpn.android.models.vpn.Server
import com.protonvpn.android.models.vpn.ServerGroup
import com.protonvpn.android.utils.hasFlag

enum class FeatureIcon {
    P2P, STREAMING, TOR, SMART_ROUTING
}

fun Server.featureIcons(): Collection<FeatureIcon> = buildList {
    if (features.hasFlag(SERVER_FEATURE_P2P))
        add(FeatureIcon.P2P)
    if (features.hasFlag(SERVER_FEATURE_TOR))
        add(FeatureIcon.TOR)
    if (features.hasFlag(SERVER_FEATURE_STREAMING))
        add(FeatureIcon.STREAMING)
}

fun ServerGroup.featureIcons(): Collection<FeatureIcon> {
    val serverIcons = serverList.flatMapTo(mutableSetOf()) { server ->
        server.featureIcons().filterNot { it == FeatureIcon.STREAMING }
    }
    val hasSmartRouting = serverList.all { !it.hostCountry.isNullOrBlank() && it.hostCountry != it.exitCountry }
    return if (hasSmartRouting) serverIcons + FeatureIcon.SMART_ROUTING else serverIcons
}
