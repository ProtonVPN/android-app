/*
 * Copyright (c) 2019 Proton Technologies AG
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

import com.protonvpn.android.models.config.UserData
import com.protonvpn.android.models.config.VpnProtocol
import com.protonvpn.android.models.profiles.Profile
import com.protonvpn.android.models.vpn.Server

class ProtonVpnBackendProvider(
    private val strongSwan: StrongSwanBackend,
    private val openVpn: OpenVpnBackend
) : VpnBackendProvider {

    override suspend fun prepareConnection(
        profile: Profile,
        server: Server,
        userData: UserData
    ): PrepareResult? {
        return when (profile.getProtocol(userData)) {
            VpnProtocol.IKEv2 -> strongSwan.prepareForConnection(profile, server, scan = false)
            VpnProtocol.OpenVPN -> openVpn.prepareForConnection(profile, server, scan = false)
            VpnProtocol.Smart ->
                strongSwan.prepareForConnection(profile, server, scan = true)
                        ?: openVpn.prepareForConnection(profile, server, scan = true)
        }
    }
}
