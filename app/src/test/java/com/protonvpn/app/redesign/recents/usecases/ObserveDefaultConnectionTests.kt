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

import app.cash.turbine.test
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.redesign.recents.data.ConnectionType
import com.protonvpn.android.redesign.recents.data.DefaultConnection
import com.protonvpn.android.redesign.recents.data.DefaultConnectionDao
import com.protonvpn.android.redesign.recents.data.DefaultConnectionEntity
import com.protonvpn.android.redesign.recents.data.RecentConnection
import com.protonvpn.android.redesign.recents.data.RecentsDao
import com.protonvpn.android.redesign.recents.usecases.GetIntentAvailability
import com.protonvpn.android.redesign.recents.usecases.ObserveDefaultConnection
import com.protonvpn.android.redesign.vpn.ui.ConnectIntentAvailability
import com.protonvpn.android.servers.ServerManager2
import com.protonvpn.android.settings.data.ApplyEffectiveUserSettings
import com.protonvpn.android.settings.data.CurrentUserLocalSettingsManager
import com.protonvpn.android.settings.data.EffectiveCurrentUserSettings
import com.protonvpn.android.settings.data.EffectiveCurrentUserSettingsFlow
import com.protonvpn.android.settings.data.LocalUserSettingsStoreProvider
import com.protonvpn.android.settings.data.SettingsFeatureFlagsFlow
import com.protonvpn.android.tv.IsTvCheck
import com.protonvpn.android.tv.settings.FakeIsTvAutoConnectFeatureFlagEnabled
import com.protonvpn.android.tv.settings.FakeIsTvCustomDnsSettingFeatureFlagEnabled
import com.protonvpn.android.tv.settings.FakeIsTvNetShieldSettingFeatureFlagEnabled
import com.protonvpn.android.vpn.usecases.FakeIsIPv6FeatureFlagEnabled
import com.protonvpn.mocks.FakeIsLanDirectConnectionsFeatureFlagEnabled
import com.protonvpn.test.shared.InMemoryDataStoreFactory
import com.protonvpn.test.shared.TestCurrentUserProvider
import com.protonvpn.test.shared.TestUser
import com.protonvpn.test.shared.createConnectIntentFastest
import com.protonvpn.test.shared.createConnectIntentFastestInCountry
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.RelaxedMockK
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class ObserveDefaultConnectionTests {

    @RelaxedMockK
    private lateinit var mockDefaultConnectionDao: DefaultConnectionDao

    @RelaxedMockK
    private lateinit var mockRecentsDao: RecentsDao

    @MockK
    private lateinit var mockIsTvCheck: IsTvCheck

    @MockK
    private lateinit var mockGetIntentAvailability: GetIntentAvailability

    @MockK
    private lateinit var mockServerManager2: ServerManager2

    private lateinit var currentUserProvider: TestCurrentUserProvider

    private lateinit var effectiveCurrentUserSettings: EffectiveCurrentUserSettings

    private lateinit var observeDefaultConnection: ObserveDefaultConnection

    private lateinit var settingsManager: CurrentUserLocalSettingsManager

    private lateinit var testScope: TestScope

    private val vpnUser = TestUser.plusUser.vpnUser

    @Before
    fun setup() {
        MockKAnnotations.init(this)

        val testDispatcher = UnconfinedTestDispatcher()

        Dispatchers.setMain(dispatcher = testDispatcher)

        testScope = TestScope(context = testDispatcher)

        currentUserProvider = TestCurrentUserProvider(vpnUser = vpnUser)

        settingsManager = CurrentUserLocalSettingsManager(
            userSettingsStoreProvider = LocalUserSettingsStoreProvider(
                factory = InMemoryDataStoreFactory(),
            )
        )

        val currentUser = CurrentUser(currentUserProvider)

        every { mockIsTvCheck() } returns false

        val applyEffectiveUserSettings = ApplyEffectiveUserSettings(
            mainScope = testScope.backgroundScope,
            currentUser = currentUser,
            isTv = mockIsTvCheck,
            flags = SettingsFeatureFlagsFlow(
                isIPv6FeatureFlagEnabled = FakeIsIPv6FeatureFlagEnabled(enabled = true),
                isDirectLanConnectionsFeatureFlagEnabled = FakeIsLanDirectConnectionsFeatureFlagEnabled(enabled = true),
                isTvAutoConnectFeatureFlagEnabled = FakeIsTvAutoConnectFeatureFlagEnabled(enabled = true),
                isTvNetShieldSettingFeatureFlagEnabled = FakeIsTvNetShieldSettingFeatureFlagEnabled(enabled = true),
                isTvCustomDnsSettingFeatureFlagEnabled = FakeIsTvCustomDnsSettingFeatureFlagEnabled(enabled = true),
            )
        )

        val effectiveCurrentUserSettingsFlow = EffectiveCurrentUserSettingsFlow(
            rawCurrentUserSettingsFlow = settingsManager.rawCurrentUserSettingsFlow,
            applyEffectiveUserSettings = applyEffectiveUserSettings,
        )

        effectiveCurrentUserSettings = EffectiveCurrentUserSettings(
            mainScope = testScope.backgroundScope,
            effectiveCurrentUserSettingsFlow = effectiveCurrentUserSettingsFlow,
        )

        observeDefaultConnection = ObserveDefaultConnection(
            currentUser = currentUser,
            defaultConnectionDao = mockDefaultConnectionDao,
            effectiveCurrentUserSettings = effectiveCurrentUserSettings,
            getIntentAvailability = mockGetIntentAvailability,
            recentsDao = mockRecentsDao,
            serverManager2 = mockServerManager2,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `GIVEN default connection is not set WHEN observing default connection THEN emits corresponding default connection`() = testScope.runTest {
        val userId = vpnUser.userId
        val currentDefaultConnection = null

        listOf(
            true to DefaultConnection.FastestConnection,
            false to DefaultConnection.LastConnection,
        ).forEach { (hasAnyCountry, expectedDefaultConnection) ->
            coEvery { mockServerManager2.hasAnyCountryFlow } returns flowOf(hasAnyCountry)
            coEvery { mockDefaultConnectionDao.getDefaultConnectionFlow(userId) } returns flowOf(currentDefaultConnection)

            observeDefaultConnection().test {
                val defaultConnection = awaitItem()

                assertEquals(expectedDefaultConnection, defaultConnection)
            }
        }
    }

    @Test
    fun `GIVEN default connection is FastestConnection WHEN observing default connection THEN emits corresponding default connection`() = testScope.runTest {
        val userId = vpnUser.userId
        val currentDefaultConnectionEntity = DefaultConnectionEntity(
            userId = userId.id,
            recentId = null,
            connectionType = ConnectionType.FASTEST,
        )

        listOf(
            true to DefaultConnection.FastestConnection,
            false to DefaultConnection.LastConnection,
        ).forEach { (hasAnyCountry, expectedDefaultConnection) ->
            coEvery { mockServerManager2.hasAnyCountryFlow } returns flowOf(hasAnyCountry)
            coEvery { mockDefaultConnectionDao.getDefaultConnectionFlow(userId) } returns flowOf(currentDefaultConnectionEntity)

            observeDefaultConnection().test {
                val defaultConnection = awaitItem()

                assertEquals(expectedDefaultConnection, defaultConnection)
            }
        }
    }

    @Test
    fun `GIVEN default connection is LastConnection WHEN observing default connection THEN emits LastConnection`() = testScope.runTest {
        val userId = vpnUser.userId
        val currentDefaultConnectionEntity = DefaultConnectionEntity(
            userId = userId.id,
            recentId = null,
            connectionType = ConnectionType.LAST_CONNECTION,
        )
        val expectedDefaultConnection = DefaultConnection.LastConnection

        listOf(true, false).forEach { hasAnyCountry ->
            coEvery { mockServerManager2.hasAnyCountryFlow } returns flowOf(hasAnyCountry)
            coEvery { mockDefaultConnectionDao.getDefaultConnectionFlow(userId) } returns flowOf(currentDefaultConnectionEntity)

            observeDefaultConnection().test {
                val defaultConnection = awaitItem()

                assertEquals(expectedDefaultConnection, defaultConnection)
            }
        }
    }

    @Test
    fun `GIVEN default connection is Recent AND connection is available WHEN observing default connection THEN emits Recent`() = testScope.runTest {
        val userId = vpnUser.userId
        val recentId = 1L
        val currentDefaultConnectionEntity = DefaultConnectionEntity(
            userId = userId.id,
            recentId = recentId,
            connectionType = ConnectionType.RECENT,
        )
        val recentConnection = RecentConnection.UnnamedRecent(
            id = 1,
            isPinned = false,
            connectIntent = createConnectIntentFastestInCountry(),
        )
        val expectedDefaultConnection = DefaultConnection.Recent(recentId = recentId)

        listOf(true, false).forEach { hasAnyCountry ->
            coEvery { mockServerManager2.hasAnyCountryFlow } returns flowOf(hasAnyCountry)
            coEvery { mockDefaultConnectionDao.getDefaultConnectionFlow(userId) } returns flowOf(currentDefaultConnectionEntity)
            coEvery { mockRecentsDao.getById(recentId) } returns recentConnection
            coEvery { mockGetIntentAvailability(
                connectIntent = recentConnection.connectIntent,
                vpnUser = vpnUser,
                settingsProtocol = effectiveCurrentUserSettings.protocol.first(),
            ) } returns ConnectIntentAvailability.ONLINE

            observeDefaultConnection().test {
                val defaultConnection = awaitItem()

                assertEquals(expectedDefaultConnection, defaultConnection)
            }
        }
    }

    @Test
    fun `GIVEN default connection is Recent AND connection is unavailable WHEN observing default connection THEN emits corresponding default connection`() = testScope.runTest {
        val userId = vpnUser.userId
        val recentId = 1L
        val currentDefaultConnectionEntity = DefaultConnectionEntity(
            userId = userId.id,
            recentId = recentId,
            connectionType = ConnectionType.RECENT,
        )
        val recentConnection = RecentConnection.UnnamedRecent(
            id = 1,
            isPinned = false,
            connectIntent = createConnectIntentFastest(),
        )

        listOf(
            true to DefaultConnection.FastestConnection,
            false to DefaultConnection.LastConnection,
        ).forEach { (hasAnyCountry, expectedDefaultConnection) ->
            coEvery { mockServerManager2.hasAnyCountryFlow } returns flowOf(hasAnyCountry)
            coEvery { mockDefaultConnectionDao.getDefaultConnectionFlow(userId) } returns flowOf(currentDefaultConnectionEntity)
            coEvery { mockRecentsDao.getById(recentId) } returns recentConnection
            coEvery {
                mockGetIntentAvailability(
                    connectIntent = recentConnection.connectIntent,
                    vpnUser = vpnUser,
                    settingsProtocol = effectiveCurrentUserSettings.protocol.first(),
                )
            } returns ConnectIntentAvailability.NO_SERVERS

            observeDefaultConnection().test {
                val defaultConnection = awaitItem()

                assertEquals(expectedDefaultConnection, defaultConnection)
            }
        }
    }

}
