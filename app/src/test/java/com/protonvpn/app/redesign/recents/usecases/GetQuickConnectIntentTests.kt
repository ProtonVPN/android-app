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

package com.protonvpn.app.redesign.recents.usecases

import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.redesign.recents.data.DefaultConnection
import com.protonvpn.android.redesign.recents.data.RecentConnection
import com.protonvpn.android.redesign.recents.usecases.GetDefaultConnectIntent
import com.protonvpn.android.redesign.recents.usecases.GetIntentAvailability
import com.protonvpn.android.redesign.recents.usecases.GetQuickConnectIntent
import com.protonvpn.android.redesign.recents.usecases.ObserveDefaultConnection
import com.protonvpn.android.redesign.recents.usecases.RecentsManager
import com.protonvpn.android.redesign.vpn.ui.ConnectIntentAvailability
import com.protonvpn.android.settings.data.EffectiveCurrentUserSettings
import com.protonvpn.android.settings.data.LocalUserSettings
import com.protonvpn.android.utils.Constants
import com.protonvpn.test.shared.TestCurrentUserProvider
import com.protonvpn.test.shared.TestUser
import com.protonvpn.test.shared.createConnectIntentFastest
import com.protonvpn.test.shared.createConnectIntentFastestInCountry
import com.protonvpn.test.shared.createConnectIntentGateway
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class GetQuickConnectIntentTests {

    @MockK
    private lateinit var mockGetDefaultConnectIntent: GetDefaultConnectIntent

    @MockK
    private lateinit var mockObserveDefaultConnection: ObserveDefaultConnection

    @MockK
    private lateinit var mockRecentsManager: RecentsManager

    @MockK
    private lateinit var mockGetIntentAvailability: GetIntentAvailability

    private lateinit var mostRecentConnectionFlow: MutableStateFlow<RecentConnection?>
    private lateinit var currentUser: CurrentUser
    private lateinit var testScope: TestScope
    private lateinit var testUserProvider: TestCurrentUserProvider
    private lateinit var settingsFlow: MutableStateFlow<LocalUserSettings>
    private lateinit var getQuickConnectIntent: GetQuickConnectIntent

    private val freeUser = TestUser.freeUser.vpnUser
    private val plusUser = TestUser.plusUser.vpnUser

    @Before
    fun setup() {
        MockKAnnotations.init(this)

        testScope = TestScope()
        testUserProvider = TestCurrentUserProvider(vpnUser = null)
        currentUser = CurrentUser(testUserProvider)
        settingsFlow = MutableStateFlow(LocalUserSettings.Default)
        val effectiveUserSettings =
            EffectiveCurrentUserSettings(testScope.backgroundScope, settingsFlow)
        mostRecentConnectionFlow = MutableStateFlow(null)
        every { mockRecentsManager.getMostRecentConnection() } returns mostRecentConnectionFlow
        coEvery {
            mockGetIntentAvailability(
                any(),
                any(),
                any()
            )
        } returns ConnectIntentAvailability.ONLINE
        coEvery { mockObserveDefaultConnection() } returns flowOf(Constants.DEFAULT_CONNECTION)
        getQuickConnectIntent = GetQuickConnectIntent(
            currentUser = currentUser,
            recentsManager = mockRecentsManager,
            getIntentAvailability = mockGetIntentAvailability,
            observeDefaultConnection = mockObserveDefaultConnection,
            getDefaultConnectIntent = mockGetDefaultConnectIntent,
            userSettings = effectiveUserSettings,
        )
    }

    @Test
    fun `GIVEN no VPN user WHEN getting quick connect intent THEN returns Fastest`() = testScope.runTest {
        testUserProvider.vpnUser = null
        val expectedQuickConnectIntent = createConnectIntentFastest()

        val quickConnectIntent = getQuickConnectIntent()

        assertEquals(expectedQuickConnectIntent, quickConnectIntent)
    }

    @Test
    fun `GIVEN free VPN user WHEN getting quick connect intent THEN returns Fastest`() = testScope.runTest {
        testUserProvider.vpnUser = freeUser
        val expectedQuickConnectIntent = createConnectIntentFastest()

        val quickConnectIntent = getQuickConnectIntent()

        assertEquals(expectedQuickConnectIntent, quickConnectIntent)
    }

    @Test
    fun `GIVEN paid VPN user AND default connection is available WHEN getting quick connect intent THEN return saved default connect intent`() = testScope.runTest {
        val recentId = 1L
        val intentAvailability = ConnectIntentAvailability.ONLINE
        testUserProvider.vpnUser = plusUser

        listOf(
            DefaultConnection.FastestConnection to RecentConnection.UnnamedRecent(
                id = recentId,
                isPinned = false,
                connectIntent = createConnectIntentFastest(),
            ),
            DefaultConnection.LastConnection to RecentConnection.UnnamedRecent(
                id = recentId,
                isPinned = true,
                connectIntent = createConnectIntentFastestInCountry(),
            ),
            DefaultConnection.Recent(recentId = recentId) to RecentConnection.UnnamedRecent(
                id = recentId,
                isPinned = false,
                connectIntent = createConnectIntentGateway(),
            ),
        ).forEach { (defaultConnection, recent) ->
            val expectedQuickConnectIntent = recent.connectIntent
            coEvery { mockObserveDefaultConnection() } returns flowOf(defaultConnection)
            coEvery { mockRecentsManager.getMostRecentConnection() } returns flowOf(recent)
            coEvery { mockRecentsManager.getRecentById(id = recentId) } returns recent
            coEvery {
                mockGetIntentAvailability(
                    connectIntent = recent.connectIntent,
                    vpnUser = plusUser,
                    settingsProtocol = settingsFlow.value.protocol,
                )
            } returns intentAvailability

            val quickConnectIntent = getQuickConnectIntent()

            assertEquals(expectedQuickConnectIntent, quickConnectIntent)
        }
    }

    @Test
    fun `GIVEN paid VPN user AND default connection is not available WHEN getting quick connect intent THEN return fallback connect intent`() = testScope.runTest {
        val recentId = 1L
        val intentAvailability = ConnectIntentAvailability.UNAVAILABLE_PROTOCOL
        testUserProvider.vpnUser = plusUser

        listOf(
            DefaultConnection.FastestConnection to RecentConnection.UnnamedRecent(
                id = recentId,
                isPinned = false,
                connectIntent = createConnectIntentFastest()
            ),
            DefaultConnection.LastConnection to RecentConnection.UnnamedRecent(
                id = recentId,
                isPinned = true,
                connectIntent = createConnectIntentFastestInCountry()
            ),
            DefaultConnection.Recent(recentId = recentId) to RecentConnection.UnnamedRecent(
                id = recentId,
                isPinned = false,
                connectIntent = createConnectIntentGateway(),
            ),
        ).forEach { (defaultConnection, recent) ->
            val expectedQuickConnectIntent = createConnectIntentFastest()
            coEvery { mockObserveDefaultConnection() } returns flowOf(defaultConnection)
            coEvery { mockRecentsManager.getMostRecentConnection() } returns flowOf(recent)
            coEvery { mockRecentsManager.getRecentById(id = recentId) } returns recent
            coEvery {
                mockGetIntentAvailability(
                    connectIntent = recent.connectIntent,
                    vpnUser = plusUser,
                    settingsProtocol = settingsFlow.value.protocol,
                )
            } returns intentAvailability
            coEvery {
                mockGetDefaultConnectIntent(
                    vpnUser = plusUser,
                    protocolSelection = settingsFlow.value.protocol,
                )
            } returns expectedQuickConnectIntent

            val quickConnectIntent = getQuickConnectIntent()

            assertEquals(expectedQuickConnectIntent, quickConnectIntent)
        }
    }

    @Test
    fun `GIVEN paid VPN user AND no default connection WHEN getting quick connect intent THEN return fallback connect intent`() = testScope.runTest {
        val recentId = 1L
        testUserProvider.vpnUser = plusUser

        listOf(
            DefaultConnection.LastConnection to null,
            DefaultConnection.Recent(recentId = recentId) to null,
        ).forEach { (defaultConnection, recent) ->
            val expectedQuickConnectIntent = createConnectIntentFastest()
            coEvery { mockObserveDefaultConnection() } returns flowOf(defaultConnection)
            coEvery { mockRecentsManager.getMostRecentConnection() } returns flowOf(recent)
            coEvery { mockRecentsManager.getRecentById(id = recentId) } returns recent
            coEvery {
                mockGetDefaultConnectIntent(
                    vpnUser = plusUser,
                    protocolSelection = settingsFlow.value.protocol,
                )
            } returns expectedQuickConnectIntent

            val quickConnectIntent = getQuickConnectIntent()

            assertEquals(expectedQuickConnectIntent, quickConnectIntent)
        }
    }

}
