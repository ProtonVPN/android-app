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

package com.protonvpn.android.tv.usecases

import android.content.Context
import com.protonvpn.android.R
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.models.vpn.VpnCountry
import com.protonvpn.android.servers.GetStreamingServices
import com.protonvpn.android.tv.models.CountryCard
import com.protonvpn.android.tv.models.DrawableImage
import com.protonvpn.android.utils.CountryTools
import dagger.Reusable
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import me.proton.core.presentation.R as CoreR

@Reusable
class GetCountryCard @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val currentUser: CurrentUser
) {
    operator fun invoke(country: VpnCountry) = CountryCard(
        countryName = country.countryName,
        backgroundImage = DrawableImage(CountryTools.getLargeFlagResource(appContext, country.flag)),
        bottomTitleResId = countryListItemIcon(country),
        vpnCountry = country
    )

    private fun countryListItemIcon(country: VpnCountry) = when {
        country.isUnderMaintenance() -> CoreR.drawable.ic_proton_wrench
        currentUser.vpnUserCached()?.isFreeUser != true -> null
        country.hasAccessibleServer(currentUser.vpnUserCached()) -> R.drawable.ic_free
        else -> CoreR.drawable.ic_proton_lock_filled
    }
}
