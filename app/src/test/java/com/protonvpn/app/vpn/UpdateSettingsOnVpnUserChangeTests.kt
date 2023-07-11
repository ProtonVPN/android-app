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

import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.protonvpn.android.ProtonApplication
import com.protonvpn.android.auth.data.VpnUser
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.models.profiles.Profile
import com.protonvpn.android.models.vpn.Server
import com.protonvpn.android.netshield.NetShieldProtocol
import com.protonvpn.android.settings.data.CurrentUserLocalSettingsManager
import com.protonvpn.android.settings.data.LocalUserSettingsStoreProvider
import com.protonvpn.android.utils.AndroidUtils
import com.protonvpn.android.utils.AndroidUtils.isTV
import com.protonvpn.android.utils.Constants
import com.protonvpn.android.utils.ServerManager
import com.protonvpn.android.utils.UserPlanManager
import com.protonvpn.android.vpn.UpdateSettingsOnVpnUserChange
import com.protonvpn.test.shared.InMemoryDataStoreFactory
import com.protonvpn.test.shared.MockedServers
import com.protonvpn.test.shared.TestUser
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockkObject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
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

    @MockK private lateinit var context: Context

    @MockK private lateinit var mockCurrentUser: CurrentUser

    @MockK private lateinit var mockServerManager: ServerManager

    @MockK private lateinit var mockDefaultServer: Server

    @MockK private lateinit var mockPlanManager: UserPlanManager

    private val plusUser = TestUser.plusUser.vpnUser
    private lateinit var testScope: TestScope
    private lateinit var defaultProfile: Profile
    private lateinit var vpnUserFlow: MutableStateFlow<VpnUser?>
    private lateinit var planFlow: MutableSharedFlow<UserPlanManager.InfoChange.PlanChange>

    private lateinit var userSettingsManager: CurrentUserLocalSettingsManager
    private lateinit var updateSettingsOnVpnUserChange: UpdateSettingsOnVpnUserChange

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        ProtonApplication.setAppContextForTest(context)
        mockkObject(AndroidUtils)
        every { context.isTV() } returns false
        val testDispatcher = UnconfinedTestDispatcher()
        testScope = TestScope(testDispatcher)

        vpnUserFlow = MutableStateFlow(null)
        planFlow = MutableSharedFlow()
        defaultProfile = Profile.getTempProfile(MockedServers.server)

        every { mockServerManager.defaultConnection } returns defaultProfile
        every { mockDefaultServer.tier } returns 0
        every { mockPlanManager.planChangeFlow } returns planFlow
        every { mockServerManager.getServerForProfile(defaultProfile, any()) } answers {
            mockDefaultServer.takeIf { (arg<VpnUser>(1).maxTier ?: 0) >= it.tier }
        }

        every { mockCurrentUser.vpnUserFlow } returns vpnUserFlow

        userSettingsManager = CurrentUserLocalSettingsManager(
            mockCurrentUser,
            LocalUserSettingsStoreProvider(InMemoryDataStoreFactory())
        )

        updateSettingsOnVpnUserChange = UpdateSettingsOnVpnUserChange(
            context,
            testScope.backgroundScope,
            mockCurrentUser,
            mockServerManager,
            userSettingsManager,
            mockPlanManager
        )
    }
    @Test
    fun `upgrade changes netshield value to default`() = testScope.runTest {
        vpnUserFlow.value = plusUser
        assertEquals(
            NetShieldProtocol.ENABLED,
            userSettingsManager.rawCurrentUserSettingsFlow.first().netShield
        )
        planFlow.emit(UserPlanManager.InfoChange.PlanChange.Upgrade)
        assertEquals(
            Constants.DEFAULT_NETSHIELD_AFTER_UPGRADE,
            userSettingsManager.rawCurrentUserSettingsFlow.first().netShield
        )
    }

    @Test
    fun `paid features reverted to defaults when switching to free plan`() = testScope.runTest {
        vpnUserFlow.value = plusUser
        userSettingsManager.update { settings ->
            settings.copy(
                netShield = NetShieldProtocol.ENABLED_EXTENDED,
                randomizedNat = false,
                safeMode = false,
                secureCore = true
            )
        }

        vpnUserFlow.value = plusUser.copy(maxTier = 0)

        val updatedSettings = userSettingsManager.rawCurrentUserSettingsFlow.first()
        assertFalse(updatedSettings.secureCore)
        assertEquals(true, updatedSettings.safeMode)
        assertTrue(updatedSettings.randomizedNat)
        assertEquals(NetShieldProtocol.ENABLED, updatedSettings.netShield)
    }

    @Test
    fun `default profile cleared when plan downgraded below server's tier`() = testScope.runTest {
        vpnUserFlow.value = plusUser
        userSettingsManager.updateDefaultProfile(defaultProfile.id)
        every { mockDefaultServer.tier } returns 2

        vpnUserFlow.value = plusUser.copy(maxTier = 1)

        assertNull(userSettingsManager.rawCurrentUserSettingsFlow.first().defaultProfileId)
    }

    @Test
    fun `default profile cleared when plan downgraded below server's tier and ServerManager returns inaccessible server`() = testScope.runTest {
        vpnUserFlow.value = plusUser
        userSettingsManager.updateDefaultProfile(defaultProfile.id)
        every { mockDefaultServer.tier } returns 2

        // The real ServerManager may return a server that the user has no access too, see getBestScoreServer.
        every { mockServerManager.getServerForProfile(defaultProfile, any()) } answers {
            mockDefaultServer
        }

        vpnUserFlow.value = plusUser.copy(maxTier = 1)

        assertNull(userSettingsManager.rawCurrentUserSettingsFlow.first().defaultProfileId)
    }

    @Test
    fun `default profile not cleared when plan downgraded at server's tier`() = testScope.runTest {
        vpnUserFlow.value = plusUser
        userSettingsManager.updateDefaultProfile(defaultProfile.id)
        every { mockDefaultServer.tier } returns 1

        vpnUserFlow.value = plusUser.copy(maxTier = 1)

        assertEquals(defaultProfile.id, userSettingsManager.rawCurrentUserSettingsFlow.first().defaultProfileId)
    }

    @Test
    fun `no settings cleared when logging out and back in`() = testScope.runTest {
        vpnUserFlow.value = plusUser
        val initialSettings = userSettingsManager.update { settings ->
            settings.copy(
                defaultProfileId = defaultProfile.id,
                netShield = NetShieldProtocol.ENABLED_EXTENDED,
                randomizedNat = false,
                safeMode = false,
                secureCore = true
            )
        }
        every { mockDefaultServer.tier } returns 1

        vpnUserFlow.value = null // Simulate logout.
        vpnUserFlow.value = plusUser

        assertEquals(initialSettings, userSettingsManager.rawCurrentUserSettingsFlow.first())
    }
}
