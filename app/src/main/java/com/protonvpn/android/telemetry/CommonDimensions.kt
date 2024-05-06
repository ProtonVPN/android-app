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

package com.protonvpn.android.telemetry

import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.ui.home.ServerListUpdaterPrefs
import com.protonvpn.android.vpn.VpnStateMonitor
import dagger.Reusable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import me.proton.core.auth.domain.usecase.IsCredentialLessEnabled
import me.proton.core.user.domain.extension.isCredentialLess
import javax.inject.Inject

@Reusable
class CommonDimensions @Inject constructor(
    currentUser: CurrentUser,
    private val vpnStateMonitor: VpnStateMonitor,
    private val prefs: ServerListUpdaterPrefs,
    private val isCredentialLessEnabled: IsCredentialLessEnabled,
) {
    private val currentUserTier = currentUser.jointUserFlow.map { jointUser ->
        if (jointUser == null)
            "non-user"
        else {
            val (user, vpnUser) = jointUser
            when {
                user.isCredentialLess() -> "credential-less"
                vpnUser.userTier == 0 -> "free"
                vpnUser.userTier in 1..2 -> "paid"
                else -> "internal"
            }
        }
    }

    enum class Key {
        ISP,
        USER_COUNTRY,
        VPN_STATUS,
        USER_TIER,
        IS_CREDENTIAL_LESS_ENABLED;

        val reportedName = name.lowercase()
    }

    suspend fun add(dimensions: MutableMap<String, String>, vararg keys: Key) {
        suspend fun dimension(key: Key, value: suspend () -> String) {
            if (keys.contains(key)) dimensions[key.reportedName] = value()
        }

        dimension(Key.ISP) { prefs.lastKnownIsp ?: NO_VALUE }
        dimension(Key.USER_COUNTRY) { prefs.lastKnownCountry?.uppercase() ?: NO_VALUE }
        dimension(Key.VPN_STATUS) { if (vpnStateMonitor.isConnected) "on" else "off" }
        dimension(Key.IS_CREDENTIAL_LESS_ENABLED) { if (isCredentialLessEnabled()) "yes" else "no" }
        dimension(Key.USER_TIER) { currentUserTier.first() }
    }

    companion object {
        const val NO_VALUE = "n/a"
    }
}
