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

package com.protonvpn.tests.api

import com.protonvpn.android.appconfig.AppConfig
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.auth.usecase.OnSessionClosed
import com.protonvpn.mocks.TestApiConfig
import com.protonvpn.test.shared.TestUser
import com.protonvpn.testRules.ProtonHiltAndroidRule
import com.protonvpn.testRules.SetLoggedInUserRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import me.proton.core.account.domain.entity.Account
import me.proton.core.network.domain.HttpResponseCodes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import javax.inject.Inject

@HiltAndroidTest
class RefreshTokenTests {

    private val protonRule = ProtonHiltAndroidRule(this, TestApiConfig.Mocked(TestUser.plusUser))

    @get:Rule
    val rules = RuleChain
        .outerRule(protonRule)
        .around(SetLoggedInUserRule(TestUser.plusUser))

    @Inject
    lateinit var appConfig: AppConfig
    @Inject
    lateinit var currentUser: CurrentUser
    @Inject
    lateinit var sessionClosed: OnSessionClosed

    @Before
    fun setup() {
        protonRule.inject()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun invalidRefreshTokenWhenNoUiLogsOut() = runTest {
        protonRule.mockDispatcher.prependRules {
            rule(get, path eq "/vpn/v2/clientconfig") {
                respond(HttpResponseCodes.HTTP_UNAUTHORIZED)
            }
            rule(post, path eq "/auth/refresh") {
                respond(HttpResponseCodes.HTTP_UNPROCESSABLE)
            }
        }
        val logoutEvents = mutableListOf<Account>()
        val collectJob = launch {
            sessionClosed.logoutFlow.collect {
                logoutEvents += it
            }
        }
        appConfig.forceUpdate(currentUser.sessionId())
        collectJob.cancel()
        assertFalse(currentUser.isLoggedIn())
        assertEquals(1, logoutEvents.size)
    }
}
