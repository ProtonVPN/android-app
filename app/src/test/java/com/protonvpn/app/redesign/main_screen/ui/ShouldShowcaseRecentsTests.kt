/*
 * Copyright (c) 2024. Proton AG
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

package com.protonvpn.app.redesign.main_screen.ui

import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.redesign.CountryId
import com.protonvpn.android.redesign.main_screen.ui.ShouldShowcaseRecents
import com.protonvpn.android.redesign.recents.data.RecentConnection
import com.protonvpn.android.redesign.recents.usecases.RecentsManager
import com.protonvpn.android.redesign.vpn.ConnectIntent
import com.protonvpn.android.ui.storage.UiStateStorage
import com.protonvpn.android.ui.storage.UiStateStoreProvider
import com.protonvpn.test.shared.InMemoryDataStoreFactory
import com.protonvpn.test.shared.TestCurrentUserProvider
import com.protonvpn.test.shared.TestUser
import io.mockk.Called
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ShouldShowcaseRecentsTests {

    @MockK
    private lateinit var mockRecents: RecentsManager

    private lateinit var recentsFlow: MutableStateFlow<List<RecentConnection>>
    private lateinit var testScope: TestScope
    private lateinit var testUserProvider: TestCurrentUserProvider
    private lateinit var uiStateStorage: UiStateStorage

    private lateinit var shouldShowcaseRecents: ShouldShowcaseRecents

    private val freeUser = TestUser.freeUser.vpnUser
    private val plusUser = TestUser.plusUser.vpnUser

    @Before
    fun setup() {
        MockKAnnotations.init(this)

        testScope = TestScope()

        testUserProvider = TestCurrentUserProvider(plusUser)
        val currentUser = CurrentUser(testUserProvider)
        uiStateStorage = UiStateStorage(UiStateStoreProvider(InMemoryDataStoreFactory()), currentUser)

        recentsFlow = MutableStateFlow(emptyList())
        every { mockRecents.getRecentsList(any()) } returns recentsFlow

        shouldShowcaseRecents = ShouldShowcaseRecents(mockRecents, uiStateStorage, currentUser)
    }

    @Test
    fun `when there are no recents don't trigger the showcase`() = testScope.runTest {
        assertFalse(shouldShowcaseRecents(ConnectIntent.Fastest))
    }

    @Test
    fun `when the only recent is in connection card don't trigger the showcase`() = testScope.runTest {
        val connectIntent = ConnectIntent.FastestInCountry(CountryId.sweden, emptySet())
        val recent = RecentConnection.UnnamedRecent(1, false, connectIntent)
        recentsFlow.value = listOf(recent)

        assertFalse(shouldShowcaseRecents(connectIntent))
    }

    @Test
    fun `when the only recent is different than connection intent then trigger the showcase`() = testScope.runTest {
        val connectIntent = ConnectIntent.FastestInCountry(CountryId.sweden, emptySet())
        val connectCardRecent = RecentConnection.UnnamedRecent(1, false, connectIntent)
        recentsFlow.value = listOf(connectCardRecent)

        assertTrue(shouldShowcaseRecents(ConnectIntent.Fastest))
    }

    @Test
    fun `when there are is any recent different than connection card then trigger the showcase`() = testScope.runTest {
        val connectIntent = ConnectIntent.FastestInCountry(CountryId.sweden, emptySet())
        val connectCardRecent = RecentConnection.UnnamedRecent(1, false, connectIntent)
        val otherRecent = RecentConnection.UnnamedRecent(2, false, ConnectIntent.Fastest)
        recentsFlow.value = listOf(connectCardRecent, otherRecent)

        assertTrue(shouldShowcaseRecents(connectIntent))
    }

    @Test
    fun `when free user is logged in then don't trigger the showcase`() = testScope.runTest {
        testUserProvider.vpnUser = freeUser
        val connectIntent = ConnectIntent.FastestInCountry(CountryId.sweden, emptySet())
        val connectCardRecent = RecentConnection.UnnamedRecent(1, false, connectIntent)
        recentsFlow.value = listOf(connectCardRecent)

        assertFalse(shouldShowcaseRecents(ConnectIntent.Fastest))
    }

    @Test
    fun `when user hasa used recents then don't trigger the showcase`() = testScope.runTest {
        uiStateStorage.update { it.copy(hasUsedRecents = true) }
        val connectIntent = ConnectIntent.FastestInCountry(CountryId.sweden, emptySet())
        val connectCardRecent = RecentConnection.UnnamedRecent(1, false, connectIntent)
        recentsFlow.value = listOf(connectCardRecent)

        assertFalse(shouldShowcaseRecents(ConnectIntent.Fastest))

        // Verify that no unnecessary DB calls are made.
        verify { mockRecents wasNot Called }
    }
}
