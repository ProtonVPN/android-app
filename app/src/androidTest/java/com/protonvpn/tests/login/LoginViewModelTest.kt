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

package com.protonvpn.tests.login

import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.filters.SdkSuppress
import com.protonvpn.android.api.GuestHole
import com.protonvpn.android.api.ProtonApiRetroFit
import com.protonvpn.android.appconfig.AppConfig
import com.protonvpn.android.models.config.UserData
import com.protonvpn.android.models.login.GenericResponse
import com.protonvpn.android.models.login.LoginInfoResponse
import com.protonvpn.android.models.login.LoginResponse
import com.protonvpn.android.models.login.VPNInfo
import com.protonvpn.android.models.login.VpnInfoResponse
import com.protonvpn.android.ui.login.LoginState
import com.protonvpn.android.ui.login.LoginViewModel
import com.protonvpn.android.ui.login.ProofsProvider
import com.protonvpn.android.utils.ServerManager
import com.protonvpn.android.vpn.CertificateRepository
import com.protonvpn.test.shared.TestUser
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import me.proton.core.network.domain.ApiResult
import me.proton.core.test.kotlin.CoroutinesTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import srp.Proofs

@OptIn(ExperimentalCoroutinesApi::class)
@SdkSuppress(minSdkVersion = 28) // Mocking final classes doesn't work on older API levels.
class LoginViewModelTest : CoroutinesTest {

    @get:Rule
    var rule = InstantTaskExecutorRule()

    @RelaxedMockK
    private lateinit var userData: UserData
    @RelaxedMockK
    private lateinit var appConfig: AppConfig
    @MockK
    private lateinit var api: ProtonApiRetroFit
    @MockK
    private lateinit var guestHole: GuestHole
    @MockK
    private lateinit var serverManager: ServerManager
    @MockK
    private lateinit var proofsProvider: ProofsProvider
    @RelaxedMockK
    private lateinit var certificateRepository: CertificateRepository
    @MockK
    private lateinit var mockContext: Context

    private val fakeLoginResponse = LoginResponse(
        "access-token",
        30,
        "token-type",
        "scope",
        "uid",
        "refresh token",
        "user id"
    )
    private val fakeLoginInfoResponse = LoginInfoResponse(
        1, "modulus", "server ephemeral", 1, "salt", "srp session"
    )
    private val noConnectionsVpnInfoResponse = VpnInfoResponse(
        1000,
        VPNInfo(1, 0, null, null, 1, "user", "group-id", "pass"),
        1,
        1,
        0)

    private lateinit var viewModel: LoginViewModel

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        coEvery {
            proofsProvider.getProofs(any(), any(), any())
        } returns Proofs().apply {
            clientEphemeral = byteArrayOf(1, 2, 3)
            clientProof = byteArrayOf(1, 2, 3)
        }
        coEvery { api.getAvailableDomains() } returns ApiResult.Success(GenericResponse(1000))
        every { userData.isLoggedIn() } returns false
        coEvery { api.postLogin(any()) } returns ApiResult.Success(fakeLoginResponse)
        coEvery { api.postLoginInfo(any()) } returns ApiResult.Success(fakeLoginInfoResponse)

        viewModel =
            LoginViewModel(userData, appConfig, api, guestHole, serverManager, proofsProvider, certificateRepository)
    }

    @Test
    fun successfulLogin() = coroutinesTest {
        coEvery { api.getVPNInfo() } returns ApiResult.Success(TestUser.getBasicUser().vpnInfoResponse)
        viewModel.login(mockContext, "dummy", "dummy".toByteArray())
        assertEquals(LoginState.Success, viewModel.loginState.value)
        verify { userData.setLoggedIn(TestUser.getBasicUser().vpnInfoResponse) }
    }

    @Test
    fun vpnConnectionAllocationNeeded() = coroutinesTest {
        coEvery { api.getVPNInfo() } returns ApiResult.Success(noConnectionsVpnInfoResponse)
        coEvery { api.logout() } returns ApiResult.Success(GenericResponse(1000))
        viewModel.login(mockContext, "dummy", "dummy".toByteArray())
        assertEquals(LoginState.ConnectionAllocationPrompt, viewModel.loginState.value)
        coVerify { api.logout() }
    }

    @Test
    fun noInternet() = coroutinesTest {
        val error = ApiResult.Error.NoInternet
        coEvery { api.postLogin(any()) } returns error
        viewModel.login(mockContext, "dummy", "dummy".toByteArray())
        assertEquals(LoginState.Error(error, false), viewModel.loginState.value)
    }

    @Test
    fun backFromError() = coroutinesTest {
        noInternet()
        val handled = viewModel.onBackPressed()
        assertTrue(handled)
        assertEquals(LoginState.EnterCredentials, viewModel.loginState.value)
    }

    @Test
    fun backFromNoConnectionsAssigned() = coroutinesTest {
        vpnConnectionAllocationNeeded()
        val handled = viewModel.onBackPressed()
        assertTrue(handled)
        assertEquals(LoginState.EnterCredentials, viewModel.loginState.value)
    }
}
