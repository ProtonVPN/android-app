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
    fun `eventVpnLogin emits login events`() = scope.runTest {
        turbineScope {
            val loginEvents = currentUser.eventVpnLogin.testIn(backgroundScope)
            primaryUserIdFlow.emit(null)
            primaryUserIdFlow.emit(AccountTestHelper.UserId1)
            primaryUserIdFlow.emit(null)
            primaryUserIdFlow.emit(AccountTestHelper.UserId2)
            assertEquals(AccountTestHelper.UserId1, loginEvents.awaitItem()?.userId)
            assertEquals(AccountTestHelper.UserId2, loginEvents.awaitItem()?.userId)
        }
    }

    @Test
    fun `eventVpnLogin doesn't emit when logged-in`() = scope.runTest {
        turbineScope {
            val loginEvents = currentUser.eventVpnLogin.testIn(backgroundScope)
            primaryUserIdFlow.emit(AccountTestHelper.UserId1)
            loginEvents.expectNoEvents()
        }
    }
}
