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

import com.protonvpn.android.auth.data.VpnUserDao
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.test.shared.TestVpnUser
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import me.proton.core.accountmanager.domain.AccountManager
import me.proton.core.domain.entity.UserId
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CurrentUserTests {

    @RelaxedMockK
    private lateinit var mockAccountManager: AccountManager
    @MockK
    private lateinit var mockVpnUserDao: VpnUserDao

    @Before
    fun setup() {
        MockKAnnotations.init(this)
    }

    @Test
    fun `eventVpnLogin emits login events`() = runTest {
        every { mockAccountManager.getPrimaryUserId() } returns
            flowOf(null, UserId("1"), UserId("1"), null, null, UserId("2"))
        coEvery { mockVpnUserDao.getByUserId(any()) } answers { flowOf(TestVpnUser.create(firstArg<UserId>().id)) }
        val currentUser = CurrentUser(this, mockAccountManager, mockVpnUserDao, mockk(relaxed = true))

        val loginEvents = currentUser.eventVpnLogin.toList()
        val expected = listOf(TestVpnUser.create("1"), TestVpnUser.create("2"))
        assertEquals(expected, loginEvents)
    }
}
