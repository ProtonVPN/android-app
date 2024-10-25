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

package com.protonvpn.android.redesign.home_screen.ui

import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.profiles.usecases.NewProfilesMvpEnabled
import com.protonvpn.android.ui.home.ServerListUpdaterPrefs
import com.protonvpn.android.utils.flatMapLatestNotNull
import dagger.Reusable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject

data class UpsellCarouselState(
    val roundedServerCount: Int,
    val countryCount: Int,
    val includeProfilesCard: Boolean,
)

@Reusable
class UpsellCarouselStateFlow @Inject constructor(
    currentUser: CurrentUser,
    serverListUpdaterPrefs: ServerListUpdaterPrefs,
    newProfilesMvpEnabled: NewProfilesMvpEnabled,
): Flow<UpsellCarouselState?> {

    private val stateFlow = currentUser.vpnUserFlow.flatMapLatestNotNull {
        if (it.isFreeUser) {
            val countries = serverListUpdaterPrefs.vpnCountryCount
            val servers = serverListUpdaterPrefs.vpnServerCount
            val roundedServerCount = (servers / 100) * 100
            newProfilesMvpEnabled.observe().map { hasProfiles ->
                UpsellCarouselState(
                    roundedServerCount = roundedServerCount,
                    countryCount = countries,
                    includeProfilesCard = hasProfiles
                )
            }
        } else {
            // No upsell carousel for paid users.
            flowOf(null)
        }
    }

    override suspend fun collect(collector: FlowCollector<UpsellCarouselState?>) {
        stateFlow.collect(collector)
    }
}
