/*
 * Copyright (c) 2022 Proton AG
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

package com.protonvpn.android.auth.usecase

import android.telephony.TelephonyManager
import com.protonvpn.android.api.GuestHole
import com.protonvpn.android.logging.LogCategory
import com.protonvpn.android.logging.ProtonLogger
import com.protonvpn.android.utils.mobileCountryCode
import com.protonvpn.android.vpn.ServerPing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import me.proton.core.network.domain.NetworkManager
import javax.inject.Inject
import javax.inject.Singleton

private const val HOSTNAME_TO_CHECK = "hcaptcha.com"

@Singleton
class HumanVerificationGuestHoleCheck @Inject constructor(
    private val networkManager: NetworkManager,
    private val guestHole: GuestHole,
    private val serverPing: ServerPing,
    private val telephonyManager: TelephonyManager?
) {
    operator fun invoke(scope: CoroutineScope) {
        guestHole.openForHumanVerification = scope.async {
            val mcc = telephonyManager?.mobileCountryCode()?.lowercase()
            if (!networkManager.isConnectedToNetwork() || !(mcc == null || mcc == "ir") )
                false
            else {
                ProtonLogger.logCustom(LogCategory.CONN_GUEST_HOLE, "pinging $HOSTNAME_TO_CHECK")
                val responded = serverPing.pingTcpByHostname(HOSTNAME_TO_CHECK, 443)
                ProtonLogger.logCustom(LogCategory.CONN_GUEST_HOLE, "$HOSTNAME_TO_CHECK reachable=$responded")
                !responded
            }
        }
    }
}