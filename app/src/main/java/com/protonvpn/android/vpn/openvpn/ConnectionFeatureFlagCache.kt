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
package com.protonvpn.android.vpn.openvpn

import com.protonvpn.android.vpn.IsCustomDnsFeatureFlagEnabled
import com.protonvpn.android.vpn.usecases.IsDirectLanConnectionsFeatureFlagEnabled
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConnectionFeatureFlagCache @Inject constructor(
    private val isDirectLanConnectionsFeatureFlagEnabled: IsDirectLanConnectionsFeatureFlagEnabled,
    private val isCustomDnsFeatureFlagEnabled: IsCustomDnsFeatureFlagEnabled,
) {
    var isDirectLanConnectionsEnabledCached: Boolean? = null
        private set

    var isCustomDnsEnabledCached: Boolean? = null
        private set

    suspend fun update() {
        isDirectLanConnectionsEnabledCached = isDirectLanConnectionsFeatureFlagEnabled()
        isCustomDnsEnabledCached = isCustomDnsFeatureFlagEnabled()
    }
}
