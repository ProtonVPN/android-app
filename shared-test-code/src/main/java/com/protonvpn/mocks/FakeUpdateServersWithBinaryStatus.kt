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

package com.protonvpn.mocks

import com.protonvpn.android.servers.Server
import com.protonvpn.android.servers.UpdateServersWithBinaryStatus
import dagger.Reusable
import okhttp3.internal.toImmutableList
import javax.inject.Inject

/**
 * An UpdateServersWithBinaryStatus for testing.
 * It doesn't process the binary status, instead it updates the servers with configured transformations.
 */
@Reusable
class FakeUpdateServersWithBinaryStatus @Inject constructor() : UpdateServersWithBinaryStatus {

    var updater: (List<Server>) -> List<Server>? = { list ->
        list.map { it.copy(rawIsOnline = true, isVisible = true, load = 10f, score = 1.0) }
    }

    fun mapsAllServers(transform: (Server) -> Server) {
        updater = { it.map(transform) }
    }

    override fun invoke(
        serversToUpdate: List<Server>,
        statusData: ByteArray
    ): List<Server>? = updater(serversToUpdate)?.toImmutableList()
}
