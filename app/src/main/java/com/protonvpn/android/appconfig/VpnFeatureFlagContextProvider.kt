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

package com.protonvpn.android.appconfig

import dagger.Reusable
import me.proton.core.featureflag.domain.repository.FeatureFlagContextProvider
import javax.inject.Inject

@Reusable
class VpnFeatureFlagContextProvider @Inject constructor(
    private val physicalUserCountry: UserCountryPhysical,
) : FeatureFlagContextProvider {

    override suspend fun invoke(): Map<String, String> {
        val userCountry = physicalUserCountry()
        return if (userCountry != null) mapOf(USER_COUNTRY_PROPERTY to userCountry.countryCode) else emptyMap()
    }

    companion object {
        private const val USER_COUNTRY_PROPERTY = "userCountry"
    }
}
