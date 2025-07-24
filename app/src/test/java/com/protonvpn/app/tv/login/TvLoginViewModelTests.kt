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

package com.protonvpn.app.tv.login

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.protonvpn.android.api.GuestHole
import com.protonvpn.android.api.ProtonApiRetroFit
import com.protonvpn.android.appconfig.AppConfig
import com.protonvpn.android.appconfig.ForkedSessionResponse
import com.protonvpn.android.appconfig.SessionForkSelectorResponse
import com.protonvpn.android.auth.data.VpnUser
import com.protonvpn.android.auth.data.VpnUserDao
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.auth.usecase.SetVpnUser
import com.protonvpn.android.managed.ManagedConfig
import com.protonvpn.android.models.login.GenericResponse
import com.protonvpn.android.models.login.VPNInfo
import com.protonvpn.android.models.login.VpnInfoResponse
import com.protonvpn.android.servers.UpdateServerListFromApi
import com.protonvpn.android.tv.login.TvLoginViewModel
import com.protonvpn.android.tv.login.TvLoginViewState
import com.protonvpn.android.ui.home.ServerListUpdater
import com.protonvpn.android.utils.ServerManager
import com.protonvpn.test.shared.TestCurrentUserProvider
import com.protonvpn.test.shared.TestUser
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import me.proton.core.accountmanager.domain.AccountManager
import me.proton.core.domain.entity.UserId
import me.proton.core.network.domain.ApiResult
import me.proton.core.test.kotlin.assertIs
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TvLoginViewModelTests {

    @get:Rule
    var rule = InstantTaskExecutorRule()

    @RelaxedMockK
    private lateinit var appConfig: AppConfig
    @MockK
    private lateinit var api: ProtonApiRetroFit
    @RelaxedMockK
    private lateinit var serverListUpdater: ServerListUpdater
    @MockK
    private lateinit var serverManager: ServerManager
    @RelaxedMockK
    private lateinit var vpnUserDao: VpnUserDao
    @RelaxedMockK
    private lateinit var accountManager: AccountManager
    @RelaxedMockK
    private lateinit var guestHole: GuestHole

    private lateinit var currentUser: CurrentUser
    private lateinit var testScope: TestScope
    private lateinit var managedConfig: ManagedConfig

    private val selector = "selector"
    private val forkedSessionResponse = ForkedSessionResponse(
        expiresIn = 0,
        tokenType = "token type",
        uid = "1",
        refreshToken = "refresh token",
        payload = null,
        localId = 0,
        scopes = arrayOf("scope"),
        userId = "user ID"
    )
    private val noConnectionsVpnInfoResponse = VpnInfoResponse(
        1000,
        VPNInfo(1, 0, null, null, null, 1, "user", "group-id", "pass"),
        1,
        1,
        0,
        0,
        false
    )

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        testScope = TestScope()
        currentUser = CurrentUser(TestCurrentUserProvider(null))
        managedConfig = ManagedConfig(MutableStateFlow(null))

        coEvery { api.getAvailableDomains() } returns ApiResult.Success(GenericResponse(1000))

        coEvery { api.getSessionForkSelector() } returns ApiResult.Success(
            SessionForkSelectorResponse(selector, "USERC0D3")
        )
        coEvery { api.getForkedSession(selector) } returns ApiResult.Success(forkedSessionResponse)
        coEvery { serverListUpdater.updateServerList() } returns UpdateServerListFromApi.Result.Success
    }

    @Test
    fun successfulLogin() = testScope.runTest {
        val setVpnUser = SetVpnUser(vpnUserDao, currentUser)
        val viewModel = TvLoginViewModel(currentUser, setVpnUser, appConfig, api, serverListUpdater, serverManager,
            accountManager, monoClockMs = { currentTime }, wallClock = { currentTime },
            guestHole = guestHole, managedConfig = managedConfig
        )
        val insertedVpnUser = slot<VpnUser>()
        coEvery { vpnUserDao.insertOrUpdate(capture(insertedVpnUser)) } returns Unit

        viewModel.onEnterScreen(this)
        assertEquals(TvLoginViewState.Welcome, viewModel.state.value)

        coEvery { api.getVPNInfo(any()) } returns ApiResult.Success(TestUser.basicUser.vpnInfoResponse)
        viewModel.startLogin(this)
        advanceTimeBy(500)
        assertIs<TvLoginViewState.PollingSession>(viewModel.state.value)
        advanceUntilIdle()

        assertEquals(TvLoginViewState.Success, viewModel.state.value)
        // Can't simply verify the inserted object because to VpnInfoResponse.toVpnUserEntity uses a real clock to
        // set updateTime on the object, so every call can produce a different value...
        coVerify { vpnUserDao.insertOrUpdate(any()) }
        assertEquals(insertedVpnUser.captured.userId.id, forkedSessionResponse.userId)
    }

    @Test
    fun vpnConnectionAllocationNeeded() = testScope.runTest {
        val setVpnUser = SetVpnUser(vpnUserDao, currentUser)
        val viewModel = TvLoginViewModel(currentUser, setVpnUser, appConfig, api,
            serverListUpdater, serverManager, accountManager, monoClockMs = { currentTime }, wallClock= { currentTime },
            guestHole = guestHole, managedConfig = managedConfig)
        coEvery { api.getVPNInfo(any()) } returns ApiResult.Success(noConnectionsVpnInfoResponse)
        viewModel.startLogin(this)
        advanceUntilIdle()

        assertEquals(TvLoginViewState.ConnectionAllocationPrompt, viewModel.state.value)
        coVerify { accountManager.removeAccount(UserId(forkedSessionResponse.userId)) }
    }
}
