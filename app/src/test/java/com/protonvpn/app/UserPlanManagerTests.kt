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
import com.protonvpn.android.models.config.NetShieldProtocol
import com.protonvpn.android.models.config.UserData
import com.protonvpn.android.models.login.VPNInfo
import com.protonvpn.android.models.login.VpnInfoResponse
import com.protonvpn.android.models.login.toVpnUserEntity
import com.protonvpn.android.utils.Storage
import com.protonvpn.android.utils.UserPlanManager
import com.protonvpn.android.vpn.VpnStateMonitor
import com.protonvpn.test.shared.TestUser
import com.protonvpn.test.shared.mockVpnUser
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runBlockingTest
import me.proton.core.domain.entity.UserId
import me.proton.core.network.domain.ApiResult
import me.proton.core.network.domain.session.SessionId
import org.joda.time.DateTime
import org.joda.time.Period
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@ExperimentalCoroutinesApi
class UserPlanManagerTests {

    private lateinit var manager: UserPlanManager

    @RelaxedMockK private lateinit var vpnStateMonitor: VpnStateMonitor
    @RelaxedMockK private lateinit var apiRetroFit: ProtonApiRetroFit
    @RelaxedMockK private lateinit var currentUser: CurrentUser
    @RelaxedMockK private lateinit var vpnUserDao: VpnUserDao
    @MockK lateinit var mockContext: Context

    @MockK lateinit var mockResources: Resources

    lateinit var userData: UserData
    private var vpnUser: VpnUser? = null
    private var nowMs: Long = 0L

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
        nowMs = 0L
        manager = UserPlanManager(apiRetroFit, vpnStateMonitor, currentUser, vpnUserDao, { nowMs })
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

    @Test
    fun trialStartFiresVpnInfo() = runBlockingTest {
        launch {
            val planChange = manager.planChangeFlow.first()
            Assert.assertEquals(UserPlanManager.InfoChange.PlanChange.Downgrade("vpnbasic", "free"), planChange)
        }
        changePlan(TestUser.basicUser.vpnUser, TestUser.freeUser.vpnInfoResponse)
    }

    @Test
    fun testManagerCallsVpnInfoAfterExpiration() = runBlockingTest {
        mockContext()
        launch {
            val planChange = manager.planChangeFlow.first()
            Assert.assertEquals(UserPlanManager.InfoChange.PlanChange.TrialEnded, planChange)
        }
        val expiresIn = Period.seconds(2)
        val expirationTimeMs = (DateTime(nowMs) + expiresIn).millis
        vpnUser = mockVpnTrialUser((expirationTimeMs / 1000L).toInt())
        coEvery { apiRetroFit.getVPNInfo() } returns ApiResult.Success(TestUser.freeUser.vpnInfoResponse)
        Assert.assertEquals(expiresIn, manager.getTrialPeriodFlow().first())
        nowMs = expirationTimeMs + 1
        Assert.assertNull(manager.getTrialPeriodFlow().firstOrNull())
        coVerify(exactly = 1) { apiRetroFit.getVPNInfo() }
    }

    @Test
    fun managerFiresRefreshOnTrialStart() = runBlockingTest {
        mockContext()
        vpnUser = mockVpnTrialUser()
        every { vpnStateMonitor.isConnected } returns true
        coEvery { apiRetroFit.getVPNInfo() } returns ApiResult.Success(TestUser.freeUser.vpnInfoResponse)
        manager.getTrialPeriodFlow().toList()
        coVerify(exactly = 1) { apiRetroFit.getVPNInfo() }
    }

    private suspend fun changePlan(oldUser: VpnUser, newResponse: VpnInfoResponse) {
        vpnUser = oldUser
        coEvery { apiRetroFit.getVPNInfo() } returns ApiResult.Success(newResponse)
        manager.refreshVpnInfo()
    }

    private fun mockContext() {
        every { mockContext.resources } returns mockResources
        val slot = slot<Int>()
        every {
            mockResources.getQuantityString(
                any(), capture(slot), any()
            )
        } answers { slot.captured.toString() }
    }

    private fun mockVpnTrialUser(time: Int? = null): VpnUser {
        val mockVpnInfo = VPNInfo(
            1, time ?: 0, "trial", 3, 2, "username", "16", ""
        )
        return VpnInfoResponse(0, mockVpnInfo, 4, 4, 0)
            .toVpnUserEntity(UserId("userId"), SessionId("sessionId"))
    }
}
