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

package com.protonvpn.app.redesign.home_screen.ui

import com.protonvpn.android.redesign.CountryId
import com.protonvpn.android.redesign.home_screen.ui.HomeViewModel
import com.protonvpn.android.redesign.recents.data.RecentsDao
import com.protonvpn.android.redesign.recents.ui.RecentItemViewState
import com.protonvpn.android.redesign.vpn.ui.ConnectIntentViewState
import io.mockk.MockKAnnotations
import io.mockk.coVerify
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelRecentsTests {

    @RelaxedMockK
    private lateinit var mockRecentsDao: RecentsDao

    private lateinit var testScope: TestScope

    private lateinit var homeViewModel: HomeViewModel

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        val testScheduler = TestCoroutineScheduler()
        testScope = TestScope(UnconfinedTestDispatcher(testScheduler))
        homeViewModel = HomeViewModel(
            testScope.backgroundScope,
            recentsListViewStateFlow = mockk(relaxed = true),
            vpnStatusViewStateFlow = mockk(relaxed = true),
            recentsDao = mockRecentsDao,
            vpnConnectionManager = mockk(),
            clock = testScheduler::currentTime
        )
    }

    @Test
    fun `when pinned item is removed it is deleted from DB`() = testScope.runTest {
        val recentItem = RecentItemViewState(
            1,
            ConnectIntentViewFastest,
            isPinned = true,
            isConnected = false,
            isOnline = true,
            isAvailable = true
        )
        homeViewModel.removeRecent(recentItem)
        coVerify { mockRecentsDao.delete(1) }
    }

    @Test
    fun `when pinned connected item is removed it is unpinned`() = testScope.runTest {
        val recentItem = RecentItemViewState(
            1,
            ConnectIntentViewFastest,
            isPinned = true,
            isConnected = true,
            isOnline = true,
            isAvailable = true
        )
        homeViewModel.removeRecent(recentItem)
        coVerify { mockRecentsDao.unpin(1) }
    }

    companion object {
        val ConnectIntentViewFastest =
            ConnectIntentViewState(CountryId.fastest, null, false, null, emptySet())
    }
}
