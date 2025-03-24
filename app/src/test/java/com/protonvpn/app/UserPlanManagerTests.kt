/*
 * Copyright (c) 2020 Proton AG
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
package com.protonvpn.app

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.protonvpn.android.api.ProtonApiRetroFit
import com.protonvpn.android.auth.data.VpnUser
import com.protonvpn.android.auth.data.VpnUserDao
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.auth.usecase.SetVpnUser
import com.protonvpn.android.models.login.VpnInfoResponse
import com.protonvpn.android.utils.Storage
import com.protonvpn.android.utils.UserPlanManager
import com.protonvpn.test.shared.TestUser
import com.protonvpn.test.shared.mockVpnUser
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import me.proton.core.network.domain.ApiResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UserPlanManagerTests {

    private lateinit var manager: UserPlanManager

    @RelaxedMockK private lateinit var apiRetroFit: ProtonApiRetroFit
    @RelaxedMockK private lateinit var currentUser: CurrentUser
    @RelaxedMockK private lateinit var vpnUserDao: VpnUserDao

    private var vpnUser: VpnUser? = null

    @get:Rule var rule = InstantTaskExecutorRule()

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        Storage.setPreferences(mockk(relaxed = true))
        currentUser.mockVpnUser {
            vpnUser
        }
        val userSlot = slot<VpnUser>()
        coEvery { vpnUserDao.insertOrUpdate(capture(userSlot)) } answers {
            vpnUser = userSlot.captured
        }
        manager = UserPlanManager(
            apiRetroFit,
            currentUser,
            SetVpnUser(vpnUserDao, currentUser),
            mockk(relaxed = true),
            wallClock = { 0 },
            flowOf(true),
            flowOf(true)
        )
    }

    @Test
    fun planUpgradeFiresPlanChange() = runTest(UnconfinedTestDispatcher()) {
        launch {
            val planChange = manager.planChangeFlow.first()
            with(planChange) {
                // Can't compare whole objects because user has an updateTime that is different each time...
                assertEquals("free", oldUser.userTierName)
                assertEquals("vpnplus", newUser.userTierName)
            }
        }
        changePlan(TestUser.freeUser.vpnUser, TestUser.plusUser.vpnInfoResponse)
    }

    @Test
    fun planChangeInTheSameTierFiresEvent() = runTest(UnconfinedTestDispatcher()) {
        launch {
            val planChange = manager.planChangeFlow.first()
            with(planChange) {
                assertEquals("vpnplus", oldUser.userTierName)
                assertEquals("vpnpro2023", newUser.userTierName)
            }
        }
        changePlan(TestUser.plusUser.vpnUser, TestUser.businessEssential.vpnInfoResponse)
    }

    @Test
    fun credentialChangeFiresEvent() = runTest(UnconfinedTestDispatcher()) {
        launch {
            assertTrue(UserPlanManager.InfoChange.VpnCredentials in manager.infoChangeFlow.first())
        }
        changePlan(TestUser.basicUser.vpnUser, TestUser.badUser.vpnInfoResponse)
    }

    @Test
    fun planDowngradeFiresDowngrade() = runTest(UnconfinedTestDispatcher()) {
        launch {
            val planChange = manager.planChangeFlow.first()
            with(planChange) {
                assertEquals("vpnplus", oldUser.userTierName)
                assertEquals("free", newUser.userTierName)
                assertTrue(isDowngrade)
            }
        }
        changePlan(TestUser.plusUser.vpnUser, TestUser.freeUser.vpnInfoResponse)
    }

    private suspend fun changePlan(oldUser: VpnUser, newResponse: VpnInfoResponse) {
        vpnUser = oldUser
        coEvery { apiRetroFit.getVPNInfo() } returns ApiResult.Success(newResponse)
        manager.refreshVpnInfoInternal()
    }
}
