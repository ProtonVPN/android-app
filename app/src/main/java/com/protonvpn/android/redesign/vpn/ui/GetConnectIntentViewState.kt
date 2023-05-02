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

package com.protonvpn.android.redesign.vpn.ui

import com.protonvpn.android.models.vpn.Server
import com.protonvpn.android.redesign.CountryId
import com.protonvpn.android.redesign.countries.Translator
import com.protonvpn.android.redesign.vpn.ConnectIntent
import com.protonvpn.android.redesign.vpn.ServerFeature
import com.protonvpn.android.utils.ServerManager
import dagger.Reusable
import javax.inject.Inject

@Reusable
class GetConnectIntentViewState @Inject constructor(
    private val serverManager: ServerManager,
    private val translator: Translator
) {

    operator fun invoke(connectIntent: ConnectIntent, connectedServer: Server? = null): ConnectIntentViewState =
        when (connectIntent) {
            is ConnectIntent.FastestInCountry -> fastestInCountry(connectIntent, connectedServer)
            is ConnectIntent.FastestInCity -> fastestInCity(connectIntent, connectedServer)
            is ConnectIntent.SecureCore -> secureCore(connectIntent, connectedServer)
            is ConnectIntent.Server -> specificServer(connectIntent, connectedServer)
        }

    private fun fastestInCountry(connectIntent: ConnectIntent.FastestInCountry, connectedServer: Server? = null) =
        ConnectIntentViewState(
            exitCountry = fastestOrConnectedOrIntent(connectIntent.country, connectedServer, Server::exitCountry),
            entryCountry = null,
            isSecureCore = false,
            secondaryLabel =
                connectedCountryIfFastest(connectIntent.country, connectedServer, Server::entryCountry)?.let {
                    ConnectIntentSecondaryLabel.Country(it)
                },
            serverFeatures = effectiveServerFeatures(connectIntent, connectedServer)
        )

    private fun fastestInCity(connectIntent: ConnectIntent.FastestInCity, connectedServer: Server? = null) =
        ConnectIntentViewState(
            exitCountry = connectedServer?.entryCountry?.let { CountryId(it) } ?: connectIntent.country,
            entryCountry = null,
            isSecureCore = false,
            secondaryLabel = ConnectIntentSecondaryLabel.RawText(
                connectedServer?.displayCity ?: translator.getCity(connectIntent.cityEn)
            ),
            serverFeatures = effectiveServerFeatures(connectIntent, connectedServer)
        )

    private fun secureCore(
        connectIntent: ConnectIntent.SecureCore,
        connectedServer: Server? = null
    ): ConnectIntentViewState {
        val bothFastest = connectIntent.exitCountry.isFastest && connectIntent.entryCountry.isFastest
        val bothSpecified = !connectIntent.exitCountry.isFastest && !connectIntent.entryCountry.isFastest
        val secondaryLabel = if (bothSpecified || connectedServer != null && bothFastest) {
            ConnectIntentSecondaryLabel.SecureCore(
                exit = connectedCountryIfFastest(connectIntent.exitCountry, connectedServer, Server::exitCountry),
                entry = connectedServer?.entryCountry?.let { CountryId(it) } ?: connectIntent.entryCountry
            )
        } else {
            null
        }
        return ConnectIntentViewState(
            exitCountry = fastestOrConnectedOrIntent(connectIntent.exitCountry, connectedServer, Server::exitCountry),
            entryCountry = fastestOrConnectedOrIntent(
                connectIntent.entryCountry,
                connectedServer,
                Server::entryCountry
            ),
            isSecureCore = true,
            secondaryLabel = secondaryLabel,
            serverFeatures = effectiveServerFeatures(connectIntent, connectedServer)
        )
    }

    private fun specificServer(
        connectIntent: ConnectIntent.Server,
        connectedServer: Server? = null
    ): ConnectIntentViewState {
        val server = connectedServer ?: serverManager.getServerById(connectIntent.serverId)
        return if (server != null) {
            ConnectIntentViewState(
                exitCountry = CountryId(server.exitCountry),
                entryCountry = CountryId(server.entryCountry).takeIf { server.isSecureCoreServer },
                isSecureCore = server.isSecureCoreServer,
                secondaryLabel = ConnectIntentSecondaryLabel.RawText(
                    serverSecondaryLabel(server)
                ),
                serverFeatures = effectiveServerFeatures(connectIntent, connectedServer)
            )
        } else {
            // TODO: how do we handle this case?
            ConnectIntentViewState(CountryId.fastest, null, false, null, emptySet())
        }
    }

    private fun fastestOrConnectedOrIntent(
        connectIntentCountry: CountryId,
        connectedServer: Server?,
        getCountry: (Server) -> String
    ): CountryId =
        if (connectIntentCountry.isFastest || connectedServer == null) {
            connectIntentCountry
        } else {
            CountryId(getCountry(connectedServer))
        }

    private fun connectedCountryIfFastest(
        connectIntentCountry: CountryId,
        connectedServer: Server?,
        getCountry: (Server) -> String
    ): CountryId? =
        if (connectIntentCountry.isFastest && connectedServer != null) {
            CountryId(getCountry(connectedServer))
        } else {
            null
        }

    private fun effectiveServerFeatures(
        connectIntent: ConnectIntent,
        connectedServer: Server?
    ) = if (connectedServer != null) {
        connectIntent.features.intersect(ServerFeature.fromServer(connectedServer))
    } else {
        connectIntent.features
    }

    private fun serverSecondaryLabel(server: Server): String = with(server) {
        if (isFreeServer) {
            val dashIndex = serverName.indexOf('-')
            if (dashIndex != -1) {
                serverName.drop(dashIndex + 1)
            } else {
                serverName
            }
        } else {
            listOfNotNull(
                region ?: displayCity,
                serverName.dropWhile { it != '#' }
            ).joinToString(" ")
        }
    }
}
