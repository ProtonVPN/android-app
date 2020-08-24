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
package com.protonvpn.android.api

import com.protonvpn.android.models.login.LoginResponse
import com.protonvpn.android.utils.DebugUtils.debugAssert
import com.protonvpn.android.utils.LiveEvent
import com.protonvpn.android.utils.Storage
import me.proton.core.network.domain.UserData
import me.proton.core.network.domain.humanverification.HumanVerificationHeaders

class NetworkUserData : UserData {

    val forceLogoutEvent = LiveEvent()

    private var loginResponse: LoginResponse? = Storage.load(LoginResponse::class.java)

    fun setLoginResponse(value: LoginResponse) {
        loginResponse = value
        Storage.save(loginResponse)
    }

    fun clear() {
        loginResponse = null
        Storage.delete(LoginResponse::class.java)
    }

    override val sessionUid: String
        get() = loginResponse?.uid ?: ""

    override val accessToken: String get() = loginResponse?.accessToken ?: ""
    override val refreshToken: String get() = loginResponse?.refreshToken ?: ""

    override fun updateTokens(access: String, refresh: String) {
        debugAssert { loginResponse != null }
        loginResponse?.accessToken = access
        loginResponse?.refreshToken = refresh
        Storage.save(loginResponse)
    }

    // To be implemeted in VPNAND-210
    override var humanVerificationHandler: HumanVerificationHeaders? = null

    override fun forceLogout() {
        forceLogoutEvent.emit()
    }
}
