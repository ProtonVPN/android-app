/*
 * Copyright (c) 2025. Proton AG
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
import dagger.Reusable
import me.proton.core.configuration.EnvironmentConfiguration
import javax.inject.Inject

/**
 * Test users for tests with a backend.
 */
@Reusable
class TestUserEndToEnd @Inject constructor(
    private val environmentConfiguration: EnvironmentConfiguration
) {
    val freeUser: TestUser
        get() = getFreePlanUser()
    val plusUser: TestUser
        get() = getPlusPlanUser()
    val badUser: TestUser = TestUser("Testas3", "r4nd0m", "rand", "vpnplus", "vpnplus", 2, 5)
    val visionaryBlack = TestUser("visionary", "a", "test", "visionary", "visionary", 1, 10)
    val anyPaidUserProd: TestUser = TestUser(getRandomUsername(), BuildConfig.TEST_ACCOUNT_PASSWORD, "test", "vpnplus", "vpnplus", 2, 5)

    private fun isAtlas() = environmentConfiguration.proxyToken.isNotBlank()

    private fun getPlusPlanUser(): TestUser {
        return if (isAtlas()) {
            TestUser("vpnplus", "12341234", "test", "vpnplus", "vpnplus", 2, 10)
        } else {
            TestUser("automationPlusUser", BuildConfig.TEST_ACCOUNT_PASSWORD, "test", "vpnplus", "vpnplus", 2, 5)
        }
    }

    private fun getFreePlanUser(): TestUser {
        return if (isAtlas())
            TestUser("vpnfree", "12341234", "test", "free", "free", 0, 1)
        else
            TestUser("automationFreeUser", BuildConfig.TEST_ACCOUNT_PASSWORD, "testas", "free", "free", 0, 1)
    }

    fun getRandomUsername(): String {
        val usernames = listOf(
            getPlusPlanUser().email,
            "automationBasicUser",
        )
        return usernames.random()
    }
}
