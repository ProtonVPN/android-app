/*
 * Copyright (c) 2018 Proton Technologies AG
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

class TestUser private constructor(
    val email: String,
    val password: String,
    val openVpnPassword: String,
    val planName: String,
    val maxTier: Int,
    val maxConnect: Int
) {
    val vpnInfoResponse: VpnInfoResponse
        get() {
            val info = VPNInfo(1, 0, planName, maxTier, maxConnect, email, "16",
                openVpnPassword)
            return VpnInfoResponse(1000, info, 4, 4, 0)
        }
    val vpnUser: VpnUser
        get() = vpnInfoResponse.toVpnUserEntity(UserId("userId"), SessionId("sessionId"))

    companion object {
        @JvmStatic val freeUser: TestUser
            get() = TestUser("Testas1", BuildConfig.TEST_ACCOUNT_PASSWORD, "testas", "free", 0, 1)
        val basicUser: TestUser
            get() = TestUser("Testas2", BuildConfig.TEST_ACCOUNT_PASSWORD, "testas2", "vpnbasic", 1, 2)
        @JvmStatic val plusUser: TestUser
            get() = TestUser("Testas3", BuildConfig.TEST_ACCOUNT_PASSWORD, "test", "vpnplus", 2, 5)
        val badUser: TestUser
            get() = TestUser("Testas3", "r4nd0m", "rand", "vpnplus", 2, 5)
        @JvmStatic val trialUser: TestUser
            get() = TestUser("Testas5", BuildConfig.TEST_ACCOUNT_PASSWORD, "test", "trial", 1, 2)

        fun setTrialUserAsExpired() =
            TestUser("Testas5", BuildConfig.TEST_ACCOUNT_PASSWORD, "test", "free", 1, 2)

        val forkedSessionResponse: ForkedSessionResponse
            get() = ForkedSessionResponse(
                864000,
                "Bearer",
                "UId",
                "refreshToken",
                "null",
                0, Arrays.asList("self", "user", "loggedin", "vpn").toTypedArray(),
                "UserId")
    }
}

fun CurrentUser.mockVpnUser(block: () -> VpnUser?) {
    coEvery { vpnUser() } answers { block() }
    every { vpnUserCached() } answers { block() }
}
