/*
 * Copyright (c) 2025 Proton AG
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

package com.protonvpn.android.redesign.recents.usecases

import com.protonvpn.android.auth.data.VpnUser
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.redesign.recents.data.DefaultConnection
import com.protonvpn.android.redesign.recents.data.DefaultConnectionDao
import com.protonvpn.android.redesign.recents.data.RecentsDao
import com.protonvpn.android.redesign.recents.data.toDefaultConnection
import com.protonvpn.android.redesign.vpn.ui.ConnectIntentAvailability
import com.protonvpn.android.servers.ServerManager2
import com.protonvpn.android.settings.data.EffectiveCurrentUserSettings
import com.protonvpn.android.utils.flatMapLatestNotNull
import com.protonvpn.android.vpn.ProtocolSelection
import dagger.Reusable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import javax.inject.Inject

@Reusable
class ObserveDefaultConnection @Inject constructor(
    private val currentUser: CurrentUser,
    private val effectiveCurrentUserSettings: EffectiveCurrentUserSettings,
    private val defaultConnectionDao: DefaultConnectionDao,
    private val getIntentAvailability: GetIntentAvailability,
    private val recentsDao: RecentsDao,
    private val serverManager2: ServerManager2,
) {

    operator fun invoke(): Flow<DefaultConnection> = combine(
        serverManager2.hasAnyCountryFlow,
        effectiveCurrentUserSettings.protocol,
        ::Pair,
    ).flatMapLatest { (hasCountries, protocolSelection) ->
        currentUser.vpnUserFlow.flatMapLatestNotNull { vpnUser ->
            defaultConnectionDao.getDefaultConnectionFlow(vpnUser.userId).map { entity ->
                calculateDefaultConnection(
                    currentDefaultConnection = entity?.toDefaultConnection(),
                    hasCountries = hasCountries,
                    vpnUser = vpnUser,
                    protocolSelection = protocolSelection,
                )
            }
        }
    }

    private suspend fun calculateDefaultConnection(
        currentDefaultConnection: DefaultConnection?,
        hasCountries: Boolean,
        vpnUser: VpnUser,
        protocolSelection: ProtocolSelection,
    ): DefaultConnection = when (currentDefaultConnection) {
        null,
        DefaultConnection.FastestConnection -> {
            if (hasCountries) DefaultConnection.FastestConnection
            else DefaultConnection.LastConnection
        }

        DefaultConnection.LastConnection -> {
            DefaultConnection.LastConnection
        }

        is DefaultConnection.Recent -> {
            recentsDao.getById(id = currentDefaultConnection.recentId)
                ?.let { recent ->
                    val intentAvailability = getIntentAvailability(
                        connectIntent = recent.connectIntent,
                        vpnUser = vpnUser,
                        settingsProtocol = protocolSelection,
                    )

                    when (intentAvailability) {
                        ConnectIntentAvailability.UNAVAILABLE_PLAN,
                        ConnectIntentAvailability.UNAVAILABLE_PROTOCOL,
                        ConnectIntentAvailability.AVAILABLE_OFFLINE,
                        ConnectIntentAvailability.ONLINE -> currentDefaultConnection

                        ConnectIntentAvailability.NO_SERVERS -> null
                    }
                }
                ?: calculateDefaultConnection(
                    currentDefaultConnection = null,
                    hasCountries = hasCountries,
                    vpnUser = vpnUser,
                    protocolSelection = protocolSelection,
                )
        }
    }

}
