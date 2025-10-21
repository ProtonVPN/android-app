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

package com.protonvpn.app.auth.usecase

import app.cash.turbine.turbineScope
import com.protonvpn.android.auth.data.VpnUserDao
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.auth.usecase.DefaultCurrentUserProvider
import com.protonvpn.test.shared.TestDispatcherProvider
import com.protonvpn.test.shared.TestVpnUser
import com.protonvpn.test.shared.createAccountUser
import com.protonvpn.testsHelper.AccountTestHelper
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import me.proton.core.accountmanager.domain.AccountManager
import me.proton.core.domain.entity.SessionUserId
import me.proton.core.domain.entity.UserId
import me.proton.core.user.domain.UserManager
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class CurrentUserTests {

    @MockK
    private lateinit var mockAccountManager: AccountManager
    @MockK
    private lateinit var mockUserManager: UserManager
    @MockK
    private lateinit var mockVpnUserDao: VpnUserDao

    private val primaryUserIdFlow = MutableSharedFlow<UserId?>()

    private lateinit var scope: TestScope
    private lateinit var currentUser: CurrentUser

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        val dispatcher = UnconfinedTestDispatcher()
        scope = TestScope(dispatcher)

        every { mockAccountManager.getPrimaryUserId() } returns primaryUserIdFlow
        every { mockAccountManager.getAccount(any<UserId>()) } answers {
            val userId = firstArg<UserId>()
            when (userId) {
                AccountTestHelper.UserId1 -> flowOf(AccountTestHelper.TestAccount1)
                AccountTestHelper.UserId2 -> flowOf(AccountTestHelper.TestAccount2)
                else -> flowOf(null)
            }
        }
        coEvery { mockVpnUserDao.getByUserId(any()) } answers { flowOf(TestVpnUser.create(firstArg<UserId>().id)) }
        coEvery { mockUserManager.observeUser(any<SessionUserId>()) } answers {
            flowOf(createAccountUser(firstArg<SessionUserId>()))
        }
        val currentUserProvider = DefaultCurrentUserProvider(
            scope.backgroundScope,
            TestDispatcherProvider(dispatcher),
            mockAccountManager,
            mockVpnUserDao,
            mockUserManager
        )
        currentUser = CurrentUser(currentUserProvider)
    }

    @Test
    fun `GIVEN user is logged-in WHEN observing vppLoginEvents THEN no events are emitted`() = scope.runTest {
        turbineScope {
            primaryUserIdFlow.emit(AccountTestHelper.UserId1)

            val loginEvents = currentUser.eventVpnLogin.testIn(backgroundScope)

            loginEvents.expectNoEvents()
        }
    }

    @Test
    fun `GIVEN credential less user is logged-in AND logs in with and account WHEN observing vppLoginEvents THEN event is emitted`() = scope.runTest {
        turbineScope {
            val credentialLessUserId = AccountTestHelper.UserId1
            val accountUserId = AccountTestHelper.UserId2
            val loginEvents = currentUser.eventVpnLogin.testIn(backgroundScope)
            primaryUserIdFlow.emit(credentialLessUserId)
            primaryUserIdFlow.emit(accountUserId)
            val expectedUserId = accountUserId

            val vpnUser = loginEvents.awaitItem()

            assertEquals(expectedUserId, vpnUser?.userId)
        }
    }

    @Test
    fun `GIVEN user logs in AND switches to another account WHEN observing vppLoginEvents THEN events are emitted`() = scope.runTest {
        turbineScope {
            val accountUserId1 = AccountTestHelper.UserId1
            val accountUserId2 = AccountTestHelper.UserId2
            val loginEvents = currentUser.eventVpnLogin.testIn(backgroundScope)
            primaryUserIdFlow.emit(null)
            primaryUserIdFlow.emit(accountUserId1)
            primaryUserIdFlow.emit(null)
            primaryUserIdFlow.emit(accountUserId2)

            val vpnUser1 = loginEvents.awaitItem()
            val vpnUser2 = loginEvents.awaitItem()

            assertEquals(accountUserId1, vpnUser1?.userId)
            assertEquals(accountUserId2, vpnUser2?.userId)
        }
    }

    @Test
    fun `GIVEN vpn user exists WHEN observing if has connections assigned THEN emits true`() = scope.runTest {
        turbineScope {
            val accountUserId = AccountTestHelper.UserId1
            val vpnUser = TestVpnUser.create(id = accountUserId.id)
            coEvery { mockVpnUserDao.getByUserId(userId = accountUserId) } returns flowOf(vpnUser)
            val hasAssignedConnectionsEvents = currentUser.hasConnectionsAssignedFlow.testIn(backgroundScope)
            primaryUserIdFlow.emit(accountUserId)

            val hasAssignedConnections = hasAssignedConnectionsEvents.awaitItem()

            assertTrue(hasAssignedConnections)
        }
    }

    @Test
    fun `GIVEN vpn user does not exist WHEN observing if has connections assigned THEN emits false`() = scope.runTest {
        turbineScope {
            val accountUserId = AccountTestHelper.UserId1
            val vpnUser = null
            coEvery { mockVpnUserDao.getByUserId(userId = accountUserId) } returns flowOf(vpnUser)
            val hasAssignedConnectionsEvents = currentUser.hasConnectionsAssignedFlow.testIn(backgroundScope)
            primaryUserIdFlow.emit(accountUserId)

            val hasAssignedConnections = hasAssignedConnectionsEvents.awaitItem()

            assertFalse(hasAssignedConnections)
        }
    }

}
