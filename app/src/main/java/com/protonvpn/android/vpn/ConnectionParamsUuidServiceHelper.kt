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

package com.protonvpn.android.vpn

import android.content.Intent
import com.protonvpn.android.utils.getSerializableCompat
import java.util.UUID

/**
 * A helper for VPN services to track the UUID of the most recent ConnectionParams.
 * The last known UUID should be passed to VpnConnectionManager.onVpnServiceDestroyed in onDestroy.
 */
class ConnectionParamsUuidServiceHelper {

    var last: UUID? = null
        private set

    fun onStartCommand(intent: Intent?) {
        val connectionParamsUuid: UUID? = intent?.extras?.getSerializableCompat(EXTRA_CONNECTION_PARAMS_UUID)
        if (connectionParamsUuid != null)
            last = connectionParamsUuid
    }

    companion object {
        private const val EXTRA_CONNECTION_PARAMS_UUID = "com.protonvpn.ConnectionParamsUUID"

        fun addConnectionParamsUuid(intent: Intent, uuid: UUID) {
            intent.putExtra(EXTRA_CONNECTION_PARAMS_UUID, uuid)
        }
    }
}
