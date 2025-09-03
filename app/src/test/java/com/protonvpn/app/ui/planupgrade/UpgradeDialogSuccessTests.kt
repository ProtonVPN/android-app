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
package com.protonvpn.app.ui.planupgrade

import android.app.Activity
import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.managed.ManagedConfig
import com.protonvpn.android.ui.ForegroundActivityTracker
import com.protonvpn.android.ui.planupgrade.ShowUpgradeSuccess
import com.protonvpn.android.utils.UserPlanManager
import com.protonvpn.test.shared.TestCurrentUserProvider
import com.protonvpn.test.shared.TestUser
import io.mockk.Called
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import me.proton.core.payment.domain.PurchaseManager
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UpgradeDialogSuccessTests {

    @get:Rule
    val rule = InstantTaskExecutorRule()

    private lateinit var showUpgradeSuccess: ShowUpgradeSuccess


    private var foregroundActivityFlow: MutableStateFlow<Activity?> = MutableStateFlow(null)

    @MockK
    lateinit var foregroundActivityTracker: ForegroundActivityTracker

    private lateinit var currentUser: CurrentUser

    private lateinit var testUserProvider: TestCurrentUserProvider
    private val sameAccountFreeUser = TestUser.sameIdFreeUser.vpnUser
    private val sameAccountPlusUser = TestUser.sameIdPlusUser.vpnUser
    private val sameAccountUnlimitedUser = TestUser.sameIdPlusUser.vpnUser

    private lateinit var userPlanManager: UserPlanManager
    private lateinit var testScope: TestScope

    @MockK
    private lateinit var purchaseManager: PurchaseManager

    @RelaxedMockK
    private lateinit var showDialog: ((Context, String, Boolean) -> Unit)
    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        testScope = TestScope(UnconfinedTestDispatcher())
        testUserProvider = TestCurrentUserProvider(TestUser.sameIdFreeUser.vpnUser)
        userPlanManager = UserPlanManager(
            mainScope = testScope.backgroundScope,
            api = mockk(relaxed = true),
            currentUser = mockk(relaxed = true),
            setVpnUser = mockk(relaxed = true),
            managedConfig = ManagedConfig(MutableStateFlow(null)),
            periodicUpdateManager = mockk(relaxed = true),
            wallClock = { 0L },
            inForeground = mockk()
        )
        currentUser = CurrentUser(testUserProvider)
        every { foregroundActivityTracker.foregroundActivityFlow } returns foregroundActivityFlow
        coEvery { purchaseManager.getPurchase(any()) } returns null

        showUpgradeSuccess = ShowUpgradeSuccess(
            testScope.backgroundScope,
            foregroundActivityTracker,
            userPlanManager,
            currentUser,
            mockk(relaxed = true),
            showDialog,
            purchaseManager
        )
    }

    @Test
    fun `dialog will not be triggered if user change is for different user`() = testScope.runTest {
        userPlanManager.infoChangeFlow.emit(listOf(UserPlanManager.InfoChange.PlanChange(sameAccountFreeUser, TestUser.businessEssential.vpnUser)))
        foregroundActivityFlow.emit(mockk())

        verify { showDialog wasNot Called }
    }

    @Test
    fun `dialog is not called on downgrades`() = testScope.runTest {
        userPlanManager.infoChangeFlow.emit(listOf(UserPlanManager.InfoChange.PlanChange(sameAccountPlusUser, sameAccountFreeUser)))
        foregroundActivityFlow.emit(mockk())

        verify { showDialog wasNot Called }
    }

    @Test
    fun `dialog is called again if user downgrades and upgrades`() = testScope.runTest {
        userPlanManager.infoChangeFlow.emit(listOf(UserPlanManager.InfoChange.PlanChange(sameAccountFreeUser, sameAccountPlusUser)))
        foregroundActivityFlow.emit(mockk())

        userPlanManager.infoChangeFlow.emit(listOf(UserPlanManager.InfoChange.PlanChange(sameAccountPlusUser, sameAccountFreeUser)))
        foregroundActivityFlow.emit(mockk())

        userPlanManager.infoChangeFlow.emit(listOf(UserPlanManager.InfoChange.PlanChange(sameAccountFreeUser, sameAccountPlusUser)))
        foregroundActivityFlow.emit(mockk())
        verify(exactly = 2) { showDialog.invoke(any(), any(), any()) }
    }

    @Test
    fun `dialog is not show multiple times for same plan`() = testScope.runTest {
        userPlanManager.infoChangeFlow.emit(listOf(UserPlanManager.InfoChange.PlanChange(sameAccountFreeUser, sameAccountPlusUser)))
        foregroundActivityFlow.emit(mockk())

        userPlanManager.infoChangeFlow.emit(listOf(UserPlanManager.InfoChange.PlanChange(sameAccountFreeUser, sameAccountPlusUser)))
        foregroundActivityFlow.emit(mockk())

        verify(exactly = 1) { showDialog.invoke(any(), any(), any()) }
    }

    @Test
    fun `multiple plan changes in background result in one dialog call`() = testScope.runTest {
        userPlanManager.infoChangeFlow.emit(listOf(UserPlanManager.InfoChange.PlanChange(sameAccountFreeUser, sameAccountPlusUser)))
        userPlanManager.infoChangeFlow.emit(listOf(UserPlanManager.InfoChange.PlanChange(sameAccountFreeUser, sameAccountUnlimitedUser)))
        foregroundActivityFlow.emit(mockk())

        verify(exactly = 1) { showDialog.invoke(any(), any(), any()) }
    }
}
