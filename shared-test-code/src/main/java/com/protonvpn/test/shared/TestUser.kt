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

import com.protonvpn.android.BuildConfig
import com.protonvpn.android.models.login.toVpnUserEntity
import com.protonvpn.android.models.login.VpnInfoResponse
import com.protonvpn.android.models.login.VPNInfo
import com.protonvpn.android.auth.data.VpnUser
import me.proton.core.domain.entity.UserId
import com.protonvpn.android.appconfig.ForkedSessionResponse
import com.protonvpn.android.auth.usecase.CurrentUser
import io.mockk.coEvery
import io.mockk.every
import me.proton.core.network.domain.session.SessionId
import java.util.Arrays
import me.proton.core.util.kotlin.takeIfNotBlank

class TestUser private constructor(
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
        get() = vpnInfoResponse.toVpnUserEntity(UserId("userId"), SessionId("sessionId"))

    companion object {
        @JvmStatic val freeUser: TestUser
            get() = TestUser("Testas1", BuildConfig.TEST_ACCOUNT_PASSWORD, "testas", "free", "free",0, 1)
        val basicUser: TestUser
            get() = TestUser("Testas2", BuildConfig.TEST_ACCOUNT_PASSWORD, "testas2", "vpnbasic", "vpnbasic", 1, 2)
        @JvmStatic val plusUser: TestUser
            get() = getPlusPlanUser()
        val badUser: TestUser
            get() = TestUser("Testas3", "r4nd0m", "rand", "vpnplus", "vpnplus", 2, 5)
        @JvmStatic val specialCharUser: TestUser
            get() = TestUser("testSpecChar", BuildConfig.SPECIAL_CHAR_PASSWORD, "testas", "free", "free",0, 1)
        val forkedSessionResponse: ForkedSessionResponse
            get() = ForkedSessionResponse(
                864000,
                "Bearer",
                "UId",
                "refreshToken",
                "null",
                0, Arrays.asList("self", "user", "loggedin", "vpn").toTypedArray(),
                "UserId")

        private fun getPlusPlanUser(): TestUser {
            BuildConfig.BLACK_TOKEN?.takeIfNotBlank()?.let {
                return TestUser("vpnplus", "12341234", "test", "vpnplus", "vpnplus", 2, 10)
            }
            return TestUser("Testas3", BuildConfig.TEST_ACCOUNT_PASSWORD, "test", "vpnplus", "vpnplus", 2, 5)
        }
    }
}

fun CurrentUser.mockVpnUser(block: () -> VpnUser?) {
    coEvery { vpnUser() } answers { block() }
    every { vpnUserCached() } answers { block() }
}
