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

package com.protonvpn.app.vpn

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.protonvpn.android.auth.data.VpnUser
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.netshield.NetShieldProtocol
import com.protonvpn.android.models.config.UserData
import com.protonvpn.android.models.profiles.Profile
import com.protonvpn.android.models.vpn.Server
import com.protonvpn.android.utils.ServerManager
import com.protonvpn.android.utils.Storage
import com.protonvpn.android.vpn.UpdateSettingsOnVpnUserChange
import com.protonvpn.test.shared.MockedServers
import com.protonvpn.test.shared.TestUser
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import me.proton.core.test.kotlin.TestDispatcherProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UpdateSettingsOnVpnUserChangeTests {

    @get:Rule var rule = InstantTaskExecutorRule()

    @MockK private lateinit var mockCurrentUser: CurrentUser

    @MockK private lateinit var mockServerManager: ServerManager

    @MockK private lateinit var mockDefaultServer: Server

    private lateinit var defaultProfile: Profile
    private lateinit var userData: UserData
    private lateinit var vpnUserFlow: MutableStateFlow<VpnUser?>

    private lateinit var updateSettingsOnVpnUserChange: UpdateSettingsOnVpnUserChange

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        Storage.setPreferences(mockk(relaxed = true))
        userData = UserData.create()
        vpnUserFlow = MutableStateFlow(null)
        defaultProfile = Profile.getTempProfile(MockedServers.server)

        every { mockServerManager.defaultConnection } returns defaultProfile
        every { mockDefaultServer.tier } returns 0

        every { mockServerManager.getServerForProfile(defaultProfile, any()) } answers {
            mockDefaultServer.takeIf { (arg<VpnUser>(1).maxTier ?: 0) >= it.tier }
        }

        every { mockCurrentUser.vpnUserFlow } returns vpnUserFlow

        val dispatcher = UnconfinedTestDispatcher()
        updateSettingsOnVpnUserChange = UpdateSettingsOnVpnUserChange(
            TestScope(dispatcher),
            TestDispatcherProvider(dispatcher),
            mockCurrentUser,
            mockServerManager,
            userData
        )
    }

    @Test
    fun `only SecureCore disabled when switching to basic plan`() {
        vpnUserFlow.value = TestUser.plusUser.vpnUser
        userData.secureCoreEnabled = true
        userData.setNetShieldProtocol(NetShieldProtocol.ENABLED_EXTENDED)
        userData.safeModeEnabled = false
        userData.randomizedNatEnabled = false

        vpnUserFlow.value = TestUser.basicUser.vpnUser

        assertFalse(userData.secureCoreEnabled)
        assertFalse(userData.safeModeEnabled)
        assertFalse(userData.randomizedNatEnabled)
        assertEquals(NetShieldProtocol.ENABLED_EXTENDED, userData.getNetShieldProtocol(vpnUserFlow.value))
    }

    @Test
    fun `paid features disabled when switching to free plan`() {
        vpnUserFlow.value = TestUser.plusUser.vpnUser
        userData.secureCoreEnabled = true
        userData.setNetShieldProtocol(NetShieldProtocol.ENABLED_EXTENDED)
        userData.safeModeEnabled = false
        userData.randomizedNatEnabled = false

        vpnUserFlow.value = TestUser.freeUser.vpnUser

        assertFalse(userData.secureCoreEnabled)
        assertTrue(userData.safeModeEnabled)
        assertTrue(userData.randomizedNatEnabled)
        assertEquals(NetShieldProtocol.DISABLED, userData.getNetShieldProtocol(vpnUserFlow.value))
    }

    @Test
    fun `default profile cleared when plan downgraded below server's tier`() {
        vpnUserFlow.value = TestUser.plusUser.vpnUser
        userData.defaultProfileId = defaultProfile.id
        every { mockDefaultServer.tier } returns 2

        vpnUserFlow.value = TestUser.basicUser.vpnUser

        assertNull(userData.defaultProfileId)
    }

    @Test
    fun `default profile cleared when plan downgraded below server's tier and ServerManager returns inaccessible server`() {
        vpnUserFlow.value = TestUser.plusUser.vpnUser
        userData.defaultProfileId = defaultProfile.id
        every { mockDefaultServer.tier } returns 2

        // The real ServerManager may return a server that the user has no access too, see getBestScoreServer.
        every { mockServerManager.getServerForProfile(defaultProfile, any()) } answers {
            mockDefaultServer
        }

        vpnUserFlow.value = TestUser.basicUser.vpnUser

        assertNull(userData.defaultProfileId)
    }

    @Test
    fun `default profile not cleared when plan downgraded at server's tier`() {
        vpnUserFlow.value = TestUser.plusUser.vpnUser
        userData.defaultProfileId = defaultProfile.id
        every { mockDefaultServer.tier } returns 1

        vpnUserFlow.value = TestUser.basicUser.vpnUser

        assertEquals(defaultProfile.id, userData.defaultProfileId)
    }

    @Test
    fun `no settings cleared when logging out and back in`() {
        vpnUserFlow.value = TestUser.plusUser.vpnUser
        userData.secureCoreEnabled = true
        userData.setNetShieldProtocol(NetShieldProtocol.ENABLED_EXTENDED)
        userData.safeModeEnabled = false
        userData.randomizedNatEnabled = false
        userData.defaultProfileId = defaultProfile.id
        every { mockDefaultServer.tier } returns 1

        vpnUserFlow.value = null // Simulate logout.
        vpnUserFlow.value = TestUser.plusUser.vpnUser

        assertTrue(userData.secureCoreEnabled)
        assertFalse(userData.safeModeEnabled)
        assertFalse(userData.randomizedNatEnabled)
        assertEquals(NetShieldProtocol.ENABLED_EXTENDED, userData.getNetShieldProtocol(vpnUserFlow.value))
        assertEquals(defaultProfile.id, userData.defaultProfileId)
    }
}
