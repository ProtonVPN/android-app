/*
 * Copyright (c) 2025 Proton AG
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

package com.protonvpn.app.redesign.recents.usecases

import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.redesign.recents.data.ConnectionType
import com.protonvpn.android.redesign.recents.data.DefaultConnection
import com.protonvpn.android.redesign.recents.data.DefaultConnectionDao
import com.protonvpn.android.redesign.recents.data.DefaultConnectionEntity
import com.protonvpn.android.redesign.recents.usecases.SetDefaultConnection
import com.protonvpn.test.shared.TestCurrentUserProvider
import com.protonvpn.test.shared.TestUser
import io.mockk.MockKAnnotations
import io.mockk.coVerify
import io.mockk.impl.annotations.RelaxedMockK
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class SetDefaultConnectionTests {

    @RelaxedMockK
    private lateinit var mockDefaultConnectionDao: DefaultConnectionDao

    private lateinit var currentUserProvider: TestCurrentUserProvider

    private lateinit var setDefaultConnection: SetDefaultConnection

    @Before
    fun setup() {
        MockKAnnotations.init(this)

        currentUserProvider = TestCurrentUserProvider(vpnUser = null)

        setDefaultConnection = SetDefaultConnection(
            currentUser = CurrentUser(currentUserProvider),
            defaultConnectionDao = mockDefaultConnectionDao,
        )
    }

    @Test
    fun `GIVEN there's no VPN user WHEN setting default connection THEN do nothing`() = runTest {
        currentUserProvider.vpnUser = null

        listOf(
            DefaultConnection.FastestConnection,
            DefaultConnection.LastConnection,
            DefaultConnection.Recent(recentId = 1L),
        ).forEach { defaultConnection ->
            setDefaultConnection(newDefaultConnection = defaultConnection)

            coVerify(exactly = 0) { mockDefaultConnectionDao.insert(any()) }
        }
    }

    @Test
    fun `GIVEN there's a VPN user WHEN setting default connection THEN insert default connection`() = runTest {
        val vpnUser = TestUser.plusUser.vpnUser
        val recentId = 1L
        currentUserProvider.vpnUser = TestUser.plusUser.vpnUser

        listOf(
            DefaultConnection.FastestConnection to DefaultConnectionEntity(
                userId = vpnUser.userId.id,
                recentId = null,
                connectionType = ConnectionType.FASTEST,
            ),
            DefaultConnection.LastConnection to DefaultConnectionEntity(
                userId = vpnUser.userId.id,
                recentId = null,
                connectionType = ConnectionType.LAST_CONNECTION,
            ),
            DefaultConnection.Recent(recentId = recentId) to DefaultConnectionEntity(
                userId = vpnUser.userId.id,
                recentId = recentId,
                connectionType = ConnectionType.RECENT,
            ),
        ).forEach { (defaultConnection, expectedDefaultConnectionEntity) ->
            setDefaultConnection(newDefaultConnection = defaultConnection)

            coVerify(exactly = 1) {
                mockDefaultConnectionDao.insert(expectedDefaultConnectionEntity)
            }
        }
    }

}
