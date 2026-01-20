/*
 * Copyright (c) 2025. Proton AG
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

package com.protonvpn.android.profiles.usecases

import com.protonvpn.android.excludedlocations.usecases.ObserveExcludedLocations
import com.protonvpn.android.redesign.CountryId
import com.protonvpn.android.redesign.vpn.ConnectIntent
import com.protonvpn.android.servers.ServerManager2
import com.protonvpn.android.servers.ServersResult
import dagger.Reusable
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Generate a connect intent that preserves some of the settings of the input intent and can be satisfied by existing
 * servers.
 * Warning: there is no one proper way to do the transformation and some changes are quite destructive
 * (e.g. gateway -> country).
 * Never make this transformation in a way that is invisible to the user, the user needs to perform an action to accept
 * the change.
 */
@Reusable
class UpdateConnectIntentForExistingServers @Inject constructor(
    private val serverManager: ServerManager2,
    private val observeExcludedLocations: ObserveExcludedLocations,
) {

    suspend operator fun invoke(initialIntent: ConnectIntent): ConnectIntent {
        var connectIntent: ConnectIntent = initialIntent

        do {
            val hasAnyServers = serverManager.forConnectIntent(
                connectIntent = connectIntent,
                fallbackResult = false,
                excludedLocations = observeExcludedLocations().first(),
                onServersResult = ServersResult::hasServers,
            )
            if (hasAnyServers) {
                break
            }
            connectIntent = oneStepUp(connectIntent)
        } while(connectIntent != ConnectIntent.Fastest)

        if (connectIntent == ConnectIntent.Fastest && !serverManager.hasAnyCountryFlow.first()) {
            // If there are no countries, try returning a gateway intent.
            connectIntent = serverManager.getGateways()
                .firstOrNull()
                ?.let { ConnectIntent.Gateway(it.name(), null) }
                ?: connectIntent
        }
        return connectIntent
    }

    /**
     * Converts intent into a more general intent.
     * When applied multiple times then all paths converge towards ConnectIntent.Fastest.
     */
    private fun oneStepUp(intent: ConnectIntent): ConnectIntent = when(intent) {
        is ConnectIntent.FastestInCountry ->
            when {
                !intent.country.isFastest -> intent.copy(country = CountryId.fastest)
                intent.features.isNotEmpty() -> intent.copy(features = emptySet())
                else -> ConnectIntent.Fastest
            }
        is ConnectIntent.FastestInCity ->
            ConnectIntent.FastestInCountry(intent.country, intent.features)

        is ConnectIntent.FastestInState ->
            ConnectIntent.FastestInCountry(intent.country, intent.features)

        is ConnectIntent.Gateway -> when {
            intent.serverId != null -> intent.copy(serverId = null)
            else -> ConnectIntent.Fastest
        }
        is ConnectIntent.SecureCore -> when {
            intent.entryCountry != CountryId.fastest -> intent.copy(entryCountry = CountryId.fastest)
            intent.exitCountry != CountryId.fastest -> intent.copy(exitCountry = CountryId.fastest)
            else -> ConnectIntent.FastestInCountry(CountryId.fastest, emptySet())
        }
        is ConnectIntent.Server ->
            ConnectIntent.FastestInCountry(intent.exitCountry ?: CountryId.fastest, intent.features)
    }
}
