/*
 * Copyright (c) 2020 Proton Technologies AG
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

import android.content.Context
import android.content.res.Resources
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.protonvpn.android.api.ProtonApiRetroFit
import com.protonvpn.android.auth.data.VpnUser
import com.protonvpn.android.auth.data.VpnUserDao
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.models.config.UserData
import com.protonvpn.android.models.login.VpnInfoResponse
import com.protonvpn.android.utils.Storage
import com.protonvpn.android.utils.UserPlanManager
import com.protonvpn.test.shared.TestUser
import com.protonvpn.test.shared.mockVpnUser
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runBlockingTest
import me.proton.core.network.domain.ApiResult
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@ExperimentalCoroutinesApi
class UserPlanManagerTests {

    private lateinit var manager: UserPlanManager

    @RelaxedMockK private lateinit var apiRetroFit: ProtonApiRetroFit
    @RelaxedMockK private lateinit var currentUser: CurrentUser
    @RelaxedMockK private lateinit var vpnUserDao: VpnUserDao
    @MockK lateinit var mockContext: Context

    @MockK lateinit var mockResources: Resources

    lateinit var userData: UserData
    private var vpnUser: VpnUser? = null

    @get:Rule var rule = InstantTaskExecutorRule()

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        Storage.setPreferences(mockk(relaxed = true))
        userData = UserData.create()
        currentUser.mockVpnUser {
            vpnUser
        }
        val userSlot = slot<VpnUser>()
        coEvery { vpnUserDao.insertOrUpdate(capture(userSlot)) } answers {
            vpnUser = userSlot.captured
        }
        manager = UserPlanManager(apiRetroFit, currentUser, vpnUserDao)
    }

    @Test
    fun planUpgradeFiresCorrectEvent() = runBlockingTest {
        launch {
            val planChange = manager.planChangeFlow.first()
            Assert.assertEquals(UserPlanManager.InfoChange.PlanChange.Upgrade, planChange)
        }
        changePlan(TestUser.freeUser.vpnUser, TestUser.plusUser.vpnInfoResponse)
    }

    @Test
    fun credentialChangeFiresEvent() = runBlockingTest {
        launch {
            Assert.assertTrue(UserPlanManager.InfoChange.VpnCredentials in manager.infoChangeFlow.first())
        }
        changePlan(TestUser.basicUser.vpnUser, TestUser.badUser.vpnInfoResponse)
    }

    @Test
    fun planDowngradeFiresDowngrade() = runBlockingTest {
        launch {
            val planChange = manager.planChangeFlow.first()
            Assert.assertEquals(UserPlanManager.InfoChange.PlanChange.Downgrade("vpnplus", "free"), planChange)
        }
        changePlan(TestUser.plusUser.vpnUser, TestUser.freeUser.vpnInfoResponse)
    }

    private suspend fun changePlan(oldUser: VpnUser, newResponse: VpnInfoResponse) {
        vpnUser = oldUser
        coEvery { apiRetroFit.getVPNInfo() } returns ApiResult.Success(newResponse)
        manager.refreshVpnInfo()
    }
}
