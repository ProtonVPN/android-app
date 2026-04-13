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
        USER_TIER("userTier"),
        USER_TIER_LEGACY("user_tier"),
        IS_CREDENTIAL_LESS_ENABLED_LEGACY("is_credential_less_enabled")
    }

    suspend fun add(dimensions: MutableMap<String, String>, vararg keys: Key)

    suspend fun getValue(key: Key): String

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

    private data class UserTier(
        val isCredentialLess: Boolean,
        val tier: Int,
    )

    private val currentUserTier = currentUser.jointUserFlow.map { jointUser ->
        jointUser?.let {
            val (user, vpnUser) = it
            UserTier(user.isCredentialLess(), vpnUser.userTier)
        }
    }

    override suspend fun add(dimensions: MutableMap<String, String>, vararg keys: CommonDimensions.Key) {
        keys.forEach { dimensions[it.reportedName] = getValue(it) }
    }

    override suspend fun getValue(key: CommonDimensions.Key): String =
        when (key) {
            CommonDimensions.Key.ISP -> prefs.lastKnownIsp ?: CommonDimensions.NO_VALUE
            CommonDimensions.Key.USER_COUNTRY_LEGACY ->
                prefs.lastKnownCountry?.uppercase() ?: CommonDimensions.NO_VALUE
            CommonDimensions.Key.VPN_STATUS_LEGACY ->
                if (vpnStateMonitor.isConnected) "on" else "off"
            CommonDimensions.Key.USER_TIER ->
                currentUserTier.first().toTelemetry()
            CommonDimensions.Key.USER_TIER_LEGACY ->
                currentUserTier.first().toTelemetryLegacy()
            CommonDimensions.Key.IS_CREDENTIAL_LESS_ENABLED_LEGACY ->
                if (isCredentialLessEnabled()) "yes" else "no"
        }

    private fun UserTier?.toTelemetry(): String = when {
        this == null -> "non_user"
        isCredentialLess -> "credential_less"
        tier == 0 -> "free"
        tier in 1..2 -> "paid"
        else -> "internal"
    }

    private fun UserTier?.toTelemetryLegacy(): String = when {
        this == null -> "non-user"
        isCredentialLess -> "credential-less"
        tier == 0 -> "free"
        tier in 1..2 -> "paid"
        else -> "internal"
    }
}
