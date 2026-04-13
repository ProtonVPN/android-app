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
import me.proton.core.auth.domain.feature.IsCredentialLessEnabled
import me.proton.core.user.domain.extension.isCredentialLess
import javax.inject.Inject

interface CommonDimensions {

    enum class Key(val reportedName: String) {
        ISP("isp"),
        USER_COUNTRY_LEGACY("user_country"),
        VPN_STATUS_LEGACY("vpn_status"),
        USER_TIER_LEGACY("user_tier"),
        IS_CREDENTIAL_LESS_ENABLED_LEGACY("is_credential_less_enabled")
    }

    suspend fun add(dimensions: MutableMap<String, String>, vararg keys: Key)

    companion object {
        const val NO_VALUE = "n/a"
        const val UNKNOWN = "unknown"
    }
}

@Reusable
class DefaultCommonDimensions @Inject constructor(
    currentUser: CurrentUser,
    private val vpnStateMonitor: VpnStateMonitor,
    private val prefs: ServerListUpdaterPrefs,
    private val isCredentialLessEnabled: IsCredentialLessEnabled,
) : CommonDimensions {
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

    override suspend fun add(dimensions: MutableMap<String, String>, vararg keys: CommonDimensions.Key) {
        suspend fun dimension(key: CommonDimensions.Key, value: suspend () -> String) {
            if (keys.contains(key)) dimensions[key.reportedName] = value()
        }

        dimension(CommonDimensions.Key.ISP) { prefs.lastKnownIsp ?: CommonDimensions.NO_VALUE }
        dimension(CommonDimensions.Key.USER_COUNTRY_LEGACY) { prefs.lastKnownCountry?.uppercase() ?: CommonDimensions.NO_VALUE }
        dimension(CommonDimensions.Key.VPN_STATUS_LEGACY) { if (vpnStateMonitor.isConnected) "on" else "off" }
        dimension(CommonDimensions.Key.IS_CREDENTIAL_LESS_ENABLED_LEGACY) { if (isCredentialLessEnabled()) "yes" else "no" }
        dimension(CommonDimensions.Key.USER_TIER_LEGACY) { currentUserTier.first() }
    }
}
