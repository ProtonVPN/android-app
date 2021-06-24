/*
 * Copyright (c) 2021. Proton Technologies AG
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

package com.protonvpn.testsTv.login

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.filters.SdkSuppress
import com.protonvpn.android.api.ProtonApiRetroFit
import com.protonvpn.android.appconfig.AppConfig
import com.protonvpn.android.appconfig.ForkedSessionResponse
import com.protonvpn.android.appconfig.SessionForkSelectorResponse
import com.protonvpn.android.models.config.UserData
import com.protonvpn.android.models.login.GenericResponse
import com.protonvpn.android.models.login.VPNInfo
import com.protonvpn.android.models.login.VpnInfoResponse
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
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import me.proton.core.network.domain.ApiResult
import me.proton.core.test.kotlin.CoroutinesTest
import me.proton.core.test.kotlin.assertIs
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
@SdkSuppress(minSdkVersion = 28) // Mocking final classes doesn't work on older API levels.
class TvLoginViewModelTests : CoroutinesTest {

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
        VPNInfo(1, 0, null, null, 1, "user", "group-id", "pass"),
        1,
        1,
        0)

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        coEvery { api.getAvailableDomains() } returns ApiResult.Success(GenericResponse(1000))

        coEvery { api.getSessionForkSelector() } returns ApiResult.Success(
            SessionForkSelectorResponse(selector, "USERC0D3")
        )
        coEvery { api.getForkedSession(selector) } returns ApiResult.Success(forkedSessionResponse)
        coEvery { serverListUpdater.updateServerList() } returns ApiResult.Success(ServerList(listOf()))

        viewModel = TvLoginViewModel(userData, appConfig, api, serverListUpdater, serverManager, certificateRepository)
    }

    @Test
    fun successfulLogin() = coroutinesTest {
        viewModel.onEnterScreen(this)
        assertEquals(TvLoginViewState.Welcome, viewModel.state.value)

        coEvery { api.getVPNInfo() } returns ApiResult.Success(TestUser.getBasicUser().vpnInfoResponse)
        viewModel.startLogin(this)
        assertIs<TvLoginViewState.PollingSession>(viewModel.state.value)
        advanceUntilIdle()

        assertEquals(TvLoginViewState.Success, viewModel.state.value)
        verify { userData.setLoginResponse(forkedSessionResponse.toLoginResponse("invalid")) }
        verify { userData.setLoggedIn(TestUser.getBasicUser().vpnInfoResponse) }
    }

    @Test
    fun vpnConnectionAllocationNeeded() = coroutinesTest {
        coEvery { api.getVPNInfo() } returns ApiResult.Success(noConnectionsVpnInfoResponse)
        coEvery { api.logout() } returns ApiResult.Success(GenericResponse(1000))
        viewModel.startLogin(this)
        advanceUntilIdle()

        assertEquals(TvLoginViewState.ConnectionAllocationPrompt, viewModel.state.value)
        verify { userData.setLoginResponse(forkedSessionResponse.toLoginResponse("invalid")) }
        verify(exactly = 0) { userData.setLoggedIn(any<VpnInfoResponse>()) }
        coVerify { api.logout() }
    }
}
