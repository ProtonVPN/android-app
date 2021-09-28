/*
 * Copyright (c) 2020 Proton Technologies AG
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

package com.protonvpn.android.ui.home

import com.protonvpn.android.models.config.UserData
import com.protonvpn.android.utils.LiveEvent
import com.protonvpn.android.utils.ServerManager
import com.protonvpn.android.vpn.CertificateRepository
import com.protonvpn.android.vpn.VpnConnectionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import me.proton.core.accountmanager.domain.AccountManager
import me.proton.core.network.domain.session.SessionId
import me.proton.core.network.domain.session.SessionProvider

class LogoutHandler(
    val scope: CoroutineScope,
    val userData: UserData,
    val serverManager: ServerManager,
    val vpnConnectionManager: VpnConnectionManager,
    val certificateRepository: CertificateRepository,
    val accountManager: AccountManager,
    val sessionProvider: SessionProvider
) {
    val logoutEvent = LiveEvent()

    fun logout() = scope.launch {
        userData.sessionId?.let { sessionId ->
            val userId = sessionProvider.getUserId(sessionId)
            requireNotNull(userId)
            accountManager.removeAccount(userId)
            onLogout(sessionId)
        }
    }

    suspend fun onLogout(sessionId: SessionId) {
        if (userData.sessionId == sessionId) {
            vpnConnectionManager.disconnectSync()
            certificateRepository.clear(sessionId)
            userData.logout()
            serverManager.clearCache()
            logoutEvent.emit()
        }
    }
}
