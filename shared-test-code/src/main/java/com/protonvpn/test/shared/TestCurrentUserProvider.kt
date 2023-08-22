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

package com.protonvpn.test.shared

import com.protonvpn.android.auth.data.VpnUser
import com.protonvpn.android.auth.usecase.CurrentUserProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import me.proton.core.network.domain.session.SessionId
import me.proton.core.user.domain.entity.User

class TestCurrentUserProvider(
    vpnUser: VpnUser?,
    user: User? = null,
    sessionId: SessionId? = null
) : CurrentUserProvider {

    private val mutableVpnUserFlow = MutableStateFlow(vpnUser)
    private val mutableUserFlow = MutableStateFlow(user)
    private val mutableSessionIdFlow = MutableStateFlow(sessionId)

    var vpnUser: VpnUser?
        set(value) { mutableVpnUserFlow.value = value }
        get() = mutableVpnUserFlow.value

    var user: User?
        set(value) { mutableUserFlow.value = value }
        get() = mutableUserFlow.value

    var sessionId: SessionId?
        set(value) { mutableSessionIdFlow.value = value }
        get() = mutableSessionIdFlow.value

    override val vpnUserFlow: Flow<VpnUser?> = mutableVpnUserFlow
    override val userFlow: Flow<User?> = mutableUserFlow
    override val sessionIdFlow: Flow<SessionId?> = mutableSessionIdFlow
}
