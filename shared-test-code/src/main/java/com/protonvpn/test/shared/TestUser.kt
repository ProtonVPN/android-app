/*
 * Copyright (c) 2022. Proton AG
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
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.models.login.VPNInfo
import com.protonvpn.android.models.login.VpnInfoResponse
import com.protonvpn.android.models.login.toVpnUserEntity
import io.mockk.coEvery
import io.mockk.every
import me.proton.core.domain.entity.UserId
import me.proton.core.network.domain.session.SessionId

class TestUser(
    val email: String,
    val password: String,
    val openVpnPassword: String,
    val planName: String,
    val planDisplayName: String,
    val maxTier: Int,
    val maxConnect: Int
) {
    val vpnInfoResponse: VpnInfoResponse
        get() {
            val info = VPNInfo(1, 0, planName, planDisplayName, maxTier, maxConnect, email, "16",
                openVpnPassword)
            return VpnInfoResponse(1000, info, 4, 4, 0, 0, false)
        }
    val vpnUser: VpnUser
        get() = vpnInfoResponse.toVpnUserEntity(UserId(email), SessionId("sessionId"), 0, null)

    companion object {
        @JvmStatic val freeUser: TestUser
            get() = TestUser("automationFreeUser", "dummy", "test", "free", "free", 0, 1)
        val basicUser: TestUser
            get() = TestUser("automationBasicUser", "dummy", "testas2", "vpnbasic", "vpnbasic", 1, 2)
        @JvmStatic val plusUser: TestUser
            get() = TestUser("automationPlusUser", "dummy", "test", "vpnplus", "vpnplus", 2, 5)

        val sameIdFreeUser: TestUser
            get() = TestUser("Testas", "a", "rand", "free", "free", 0, 1)
        val sameIdPlusUser: TestUser
            get() = TestUser("Testas", "a", "rand", "vpnplus", "vpnplus", 2, 5)
        val sameIdUnlimitedUser: TestUser
            get() = TestUser("Testas", "a", "rand", "vpnunlimited", "vpnunlimited", 2, 5)
        val twofa: TestUser
            get() = TestUser("twofa", "a", "rand", "vpnplus", "vpnplus", 2, 5)
        val twopass: TestUser
            get() = TestUser("twopasswords", "thisisarandomp45w0rd_*&-/?", "rand", "vpnplus", "vpnplus", 2, 5)
        val businessEssential: TestUser
            get() = TestUser("businessEssential", "", "", "vpnpro2023", "VPN Essential", 2, 2)
    }
}

fun CurrentUser.mockVpnUser(block: () -> VpnUser?) {
    coEvery { vpnUser() } answers { block() }
    every { vpnUserCached() } answers { block() }
}
