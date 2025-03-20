/*
 * Copyright (c) 2023. Proton Technologies AG
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
package com.protonvpn.android.netshield

import com.protonvpn.android.R
import com.protonvpn.android.vpn.DnsOverride

sealed interface NetShieldViewState {
    val iconRes: Int
    val stateRes: Int

    data class Unavailable(val protocol: NetShieldProtocol, val dnsOverride: DnsOverride) : NetShieldViewState {
        override val iconRes = when (protocol) {
            NetShieldProtocol.DISABLED -> R.drawable.ic_netshield_off
            NetShieldProtocol.ENABLED, NetShieldProtocol.ENABLED_EXTENDED -> R.drawable.ic_netshield_f2
        }
        override val stateRes = R.string.netshield_state_unavailable
    }

    data class Available(
        val protocol: NetShieldProtocol,
        val netShieldStats: NetShieldStats
    ) : NetShieldViewState {
        val bandwidthShown = protocol != NetShieldProtocol.DISABLED
        override val iconRes = when (protocol) {
            NetShieldProtocol.DISABLED -> R.drawable.ic_netshield_off
            NetShieldProtocol.ENABLED, NetShieldProtocol.ENABLED_EXTENDED -> R.drawable.ic_netshield_f2
        }
        override val stateRes = when (protocol) {
            NetShieldProtocol.DISABLED -> R.string.netshield_state_off
            NetShieldProtocol.ENABLED, NetShieldProtocol.ENABLED_EXTENDED -> R.string.netshield_state_on
        }
    }
}
