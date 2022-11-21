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
import com.protonvpn.android.api.ProtonApiRetroFit
import com.protonvpn.android.appconfig.AppConfig
import com.protonvpn.android.appconfig.ForkedSessionResponse
import com.protonvpn.android.appconfig.SessionForkSelectorResponse
import com.protonvpn.android.auth.data.VpnUserDao
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.models.config.UserData
import com.protonvpn.android.models.login.GenericResponse
import com.protonvpn.android.models.login.VPNInfo
import com.protonvpn.android.models.login.VpnInfoResponse
import com.protonvpn.android.models.login.toVpnUserEntity
import com.protonvpn.android.models.vpn.ServerList
import com.protonvpn.android.tv.login.TvLoginViewModel
import com.protonvpn.android.tv.login.TvLoginViewState
import com.protonvpn.android.ui.home.ServerListUpdater
import com.protonvpn.android.utils.ServerManager
import com.protonvpn.android.vpn.CertificateRepository
import com.protonvpn.test.shared.TestUser
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.RelaxedMockK
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineScope
import kotlinx.coroutines.test.advanceUntilIdle
import me.proton.core.accountmanager.domain.AccountManager
import me.proton.core.domain.entity.UserId
import me.proton.core.network.domain.ApiResult
import me.proton.core.test.kotlin.CoroutinesTest
import me.proton.core.test.kotlin.assertIs
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class TvLoginViewModelTests : CoroutinesTest {

    val scope = TestCoroutineScope()

    @get:Rule
    var rule = InstantTaskExecutorRule()

    @RelaxedMockK
    private lateinit var userData: UserData
    @RelaxedMockK
    private lateinit var appConfig: AppConfig
    @MockK
    private lateinit var api: ProtonApiRetroFit
    @RelaxedMockK
    private lateinit var serverListUpdater: ServerListUpdater
    @RelaxedMockK
    private lateinit var certificateRepository: CertificateRepository
    @MockK
    private lateinit var serverManager: ServerManager
    @RelaxedMockK
    private lateinit var currentUser: CurrentUser
    @RelaxedMockK
    private lateinit var vpnUserDao: VpnUserDao
    @RelaxedMockK
    private lateinit var accountManager: AccountManager

    private lateinit var viewModel: TvLoginViewModel

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
        coEvery { api.getAvailableDomains() } returns ApiResult.Success(GenericResponse(1000))

        coEvery { api.getSessionForkSelector() } returns ApiResult.Success(
            SessionForkSelectorResponse(selector, "USERC0D3")
        )
        coEvery { api.getForkedSession(selector) } returns ApiResult.Success(forkedSessionResponse)
        coEvery { serverListUpdater.updateServerList() } returns ApiResult.Success(ServerList(listOf()))

        viewModel = TvLoginViewModel(scope, userData, currentUser, vpnUserDao, appConfig, api,
            serverListUpdater, serverManager, certificateRepository, accountManager)
    }

    @Test
    fun successfulLogin() = coroutinesTest {
        viewModel.onEnterScreen(this)
        assertEquals(TvLoginViewState.Welcome, viewModel.state.value)

        coEvery { api.getVPNInfo() } returns ApiResult.Success(TestUser.basicUser.vpnInfoResponse)
        viewModel.startLogin(this)
        assertIs<TvLoginViewState.PollingSession>(viewModel.state.value)
        advanceUntilIdle()

        assertEquals(TvLoginViewState.Success, viewModel.state.value)
        assertEquals(currentUser.vpnUser(),
            TestUser.basicUser.vpnInfoResponse.toVpnUserEntity(
                UserId(forkedSessionResponse.userId), forkedSessionResponse.sessionId))
    }

    @Test
    fun vpnConnectionAllocationNeeded() = coroutinesTest {
        coEvery { api.getVPNInfo() } returns ApiResult.Success(noConnectionsVpnInfoResponse)
        coEvery { api.logout() } returns ApiResult.Success(GenericResponse(1000))
        viewModel.startLogin(this)
        advanceUntilIdle()

        assertEquals(TvLoginViewState.ConnectionAllocationPrompt, viewModel.state.value)
        assertNull(currentUser.vpnUser())
        coVerify { api.logout() }
    }
}
