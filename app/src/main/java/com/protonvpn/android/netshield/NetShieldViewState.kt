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
package com.protonvpn.android.netshield

import com.protonvpn.android.R
import com.protonvpn.android.vpn.DnsOverride

sealed interface NetShieldViewState {
    val iconRes: Int
    val stateRes: Int

    data class Unavailable(
        val isNetShieldEnabled: Boolean,
        val dnsOverride: DnsOverride,
    ) : NetShieldViewState {

        override val iconRes = if (isNetShieldEnabled) {
            R.drawable.ic_netshield_f2
        } else {
            R.drawable.ic_netshield_off
        }

        override val stateRes = R.string.netshield_state_unavailable

    }

    data class Available(
        val isNetShieldEnabled: Boolean,
        val netShieldStats: NetShieldStats
    ) : NetShieldViewState {

        val bandwidthShown = isNetShieldEnabled

        override val iconRes = if (isNetShieldEnabled) {
            R.drawable.ic_netshield_f2
        } else {
            R.drawable.ic_netshield_off
        }

        override val stateRes = if (isNetShieldEnabled) {
            R.string.netshield_state_on
        } else {
            R.string.netshield_state_off
        }

    }

}
