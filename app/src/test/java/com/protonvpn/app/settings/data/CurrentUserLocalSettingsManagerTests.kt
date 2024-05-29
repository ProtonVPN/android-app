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

package com.protonvpn.app.settings.data

import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.models.config.VpnProtocol
import com.protonvpn.android.settings.data.CurrentUserLocalSettingsManager
import com.protonvpn.android.settings.data.LocalUserSettings
import com.protonvpn.android.settings.data.LocalUserSettingsStoreProvider
import com.protonvpn.android.settings.data.SplitTunnelingMigration
import com.protonvpn.android.settings.data.SplitTunnelingMode
import com.protonvpn.android.settings.data.SplitTunnelingSettings
import com.protonvpn.android.vpn.ProtocolSelection
import com.protonvpn.test.shared.InMemoryDataStoreFactory
import com.protonvpn.test.shared.TestCurrentUserProvider
import com.protonvpn.test.shared.TestUser
import com.protonvpn.test.shared.runWhileCollecting
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class CurrentUserLocalSettingsManagerTests {

    private lateinit var currentUserProvider: TestCurrentUserProvider
    private lateinit var testScope: TestScope

    private lateinit var currentUserSettings: CurrentUserLocalSettingsManager

    @Before
    fun setup() {
        val testDispatcher = UnconfinedTestDispatcher()
        testScope = TestScope(testDispatcher)
        currentUserProvider = TestCurrentUserProvider(TestUser.plusUser.vpnUser)
        val currentUser = CurrentUser(testScope.backgroundScope, currentUserProvider)
        val localUserSettingsStoreProvider = LocalUserSettingsStoreProvider(InMemoryDataStoreFactory())

        currentUserSettings = CurrentUserLocalSettingsManager(localUserSettingsStoreProvider)
    }

    @Test
    fun `with multiple observers they all get value on subscription`() = testScope.runTest(5_000) {
        val subscription1 = currentUserSettings.rawCurrentUserSettingsFlow.first()
        val subscription2 = currentUserSettings.rawCurrentUserSettingsFlow.first()

        assertEquals(LocalUserSettings.Default, subscription1)
        assertEquals(LocalUserSettings.Default, subscription2)
    }

    @Test
    fun `when a setting is updated then a new value is emitted`() = testScope.runTest {
        val emittedSettings = runWhileCollecting(currentUserSettings.rawCurrentUserSettingsFlow) {
            currentUserSettings.updateProtocol(ProtocolSelection(VpnProtocol.WireGuard))
        }
        assertEquals(
            listOf(ProtocolSelection.SMART, ProtocolSelection(VpnProtocol.WireGuard)),
            emittedSettings.map { it.protocol }
        )
    }

    @Ignore("VPNAND-1381")
    @Test
    fun `only current user's settings are updated`() = testScope.runTest {
        val user1Protocol = ProtocolSelection(VpnProtocol.OpenVPN)
        currentUserSettings.updateProtocol(user1Protocol)
        currentUserProvider.vpnUser = TestUser.freeUser.vpnUser

        val user2Protocol = currentUserSettings.rawCurrentUserSettingsFlow.first().protocol
        assertEquals(LocalUserSettings.Default.protocol, user2Protocol)
    }

    @Ignore("VPNAND-1381")
    @Test
    fun `when logged in user changes then their settings are emitted`() = testScope.runTest {
        val user1Protocol = ProtocolSelection(VpnProtocol.OpenVPN)
        currentUserSettings.updateProtocol(user1Protocol)

        val emittedSettings = runWhileCollecting(currentUserSettings.rawCurrentUserSettingsFlow) {
            currentUserProvider.vpnUser = TestUser.freeUser.vpnUser
        }
        assertEquals(
            listOf(user1Protocol, LocalUserSettings.Default.protocol),
            emittedSettings.map { it.protocol }
        )
    }

    @Test
    fun `migrate settings, split tunneling - excluded IPs present`() = runTest {
        val migration = SplitTunnelingMigration()
        val ips = listOf("1.2.3.4")
        val oldSplitTunneling =
            SplitTunnelingSettings(isEnabled = false, mode = SplitTunnelingMode.INCLUDE_ONLY, excludedIps = ips)
        val old = LocalUserSettings(version = 1, splitTunneling = oldSplitTunneling)

        assertTrue(migration.shouldMigrate(old))
        val new = migration.migrate(old)
        Assert.assertEquals(2, new.version)
        val expectedSplitTunneling =
            SplitTunnelingSettings(isEnabled = false, mode = SplitTunnelingMode.EXCLUDE_ONLY, excludedIps = ips)
        Assert.assertEquals(expectedSplitTunneling, new.splitTunneling)
    }

    @Test
    fun `migrate settings, split tunneling - no excluded IPs, no excluded apps`() = runTest {
        val migration = SplitTunnelingMigration()
        val oldSplitTunneling = SplitTunnelingSettings(isEnabled = true, mode = SplitTunnelingMode.INCLUDE_ONLY)
        val old = LocalUserSettings(version = 1, splitTunneling = oldSplitTunneling)
        assertTrue(migration.shouldMigrate(old))
        val new = migration.migrate(old)
        Assert.assertEquals(2, new.version)
        val expectedSplitTunneling = SplitTunnelingSettings(isEnabled = true, mode = SplitTunnelingMode.INCLUDE_ONLY)
        Assert.assertEquals(expectedSplitTunneling, new.splitTunneling)
    }
}
