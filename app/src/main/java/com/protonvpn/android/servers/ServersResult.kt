/*
 * Copyright (c) 2026 Proton AG
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

package com.protonvpn.android.servers

import com.protonvpn.android.vpn.PhysicalServer

sealed interface ServersResult {

    val hasAppliedExclusions: Boolean

    val hasServers: Boolean

    val servers: List<Server>

    data class Physical(
        val physicalServers: List<PhysicalServer>,
        override val hasAppliedExclusions: Boolean,
    ) : ServersResult {

        override val hasServers: Boolean = physicalServers.isNotEmpty()

        override val servers: List<Server> = physicalServers.map(PhysicalServer::server)

    }

    data class Regular(
        override val servers: List<Server>,
        override val hasAppliedExclusions: Boolean = false,
    ) : ServersResult {

        override val hasServers: Boolean = servers.isNotEmpty()

    }

}
