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
package com.protonvpn.android.vpn

import com.protonvpn.android.models.vpn.Server
import com.protonvpn.android.models.vpn.usecase.SupportsProtocol
import com.protonvpn.android.redesign.vpn.ConnectIntent
import com.protonvpn.android.redesign.vpn.satisfiesFeatures
import com.protonvpn.android.redesign.vpn.usecases.applyOverrides
import com.protonvpn.android.servers.ServerManager2
import com.protonvpn.android.settings.data.LocalUserSettings
import dagger.Reusable
import kotlinx.coroutines.flow.first
import javax.inject.Inject

// Get servers that are compatible with the given intent sorted by score. Compatibility here means
// that all constraints of an intent (country, gateway name, features, etc.) are satisfied by a given
// server. However for "fastest" constraint we don't return a single fastest server but a list
// (sorted by score) instead.
@Reusable
class GetOnlineServersForIntent @Inject constructor(
    val serverManager2: ServerManager2,
    private val supportsProtocol: SupportsProtocol,
) {
    suspend operator fun invoke(
        intent: ConnectIntent,
        userSettings: LocalUserSettings,
        maxTier: Int,
    ): List<Server> {
        val allServers = serverManager2.allServersByScoreFlow.first()
        val intentServers = serverManager2.forConnectIntent(
            intent,
            onFastest = { isSecureCore, features, excludedCountryId ->
                val excludedCountry = excludedCountryId?.countryCode
                allServers.asSequence().filter {
                    it.isSecureCoreServer == isSecureCore &&
                    it.satisfiesFeatures(features) &&
                    it.exitCountry != excludedCountry
                }
            },
            onFastestInGroup = { servers -> servers.sortedBy { it.score }.asSequence() },
            onServer = { sequenceOf(it) },
            emptySequence(),
        )
        val protocol = userSettings.applyOverrides(intent.settingsOverrides).protocol
        return intentServers
            .filter { it.tier <= maxTier && it.online && supportsProtocol(it, protocol) }
            .toList()
    }
}
