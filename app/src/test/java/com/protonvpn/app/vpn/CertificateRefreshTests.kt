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

import com.protonvpn.android.api.ProtonApiRetroFit
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.components.AppInUseMonitor
import com.protonvpn.android.models.vpn.CertificateResponse
import com.protonvpn.android.utils.UserPlanManager
import com.protonvpn.android.vpn.CertInfo
import com.protonvpn.android.vpn.CertRefreshScheduler
import com.protonvpn.android.vpn.CertificateKeyProvider
import com.protonvpn.android.vpn.CertificateRepository
import com.protonvpn.android.vpn.CertificateStorage
import io.mockk.Called
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestCoroutineScope
import kotlinx.coroutines.test.runBlockingTest
import me.proton.core.network.domain.ApiResult
import me.proton.core.network.domain.session.SessionId
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for certificate refresh tests.
 *
 * These tests need to be executed with instrumentation because CertificateRepository uses go libraries.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CertificateRefreshTests {

    private var currentTimeMs: Long = 0
    private lateinit var infoChangeFlow: MutableStateFlow<List<UserPlanManager.InfoChange>>
    private lateinit var appInUseFlow: MutableStateFlow<Boolean>

    @RelaxedMockK
    private lateinit var mockStorage: CertificateStorage

    @MockK
    private lateinit var mockKeyProvider: CertificateKeyProvider

    @MockK
    private lateinit var mockApi: ProtonApiRetroFit

    @MockK
    private lateinit var mockPlanManager: UserPlanManager

    @MockK
    private lateinit var mockCurrentUser: CurrentUser

    @RelaxedMockK
    private lateinit var mockRefeshScheduler: CertRefreshScheduler

    @MockK
    private lateinit var mockAppInUseMonitor: AppInUseMonitor

    @Before
    fun setup() {
        MockKAnnotations.init(this)

        currentTimeMs = NOW_MS
        infoChangeFlow = MutableStateFlow(emptyList())
        appInUseFlow = MutableStateFlow(false)

        every { mockKeyProvider.generateCertInfo() } returns CertInfo("private", "public", "x25519")
        every { mockPlanManager.infoChangeFlow } returns infoChangeFlow
        every { mockAppInUseMonitor.isInUseFlow } returns appInUseFlow
        every { mockAppInUseMonitor.isInUse } returns appInUseFlow.value
        coEvery { mockApi.getCertificate(any(), any()) } returns ApiResult.Success(CERTIFICATE_RESPONSE)
        coEvery { mockCurrentUser.sessionId() } returns SESSION_ID
        coEvery { mockStorage.get(any()) } returns CERT_INFO
    }

    @Test
    fun certificateRepository_fetches_certificate_when_user_logged_in_and_no_certificate() = runBlockingTest {
        coEvery { mockStorage.get(any()) } returns null

        withTestRepository {
            coVerify { mockApi.getCertificate(any(), any()) }
            coVerify { mockStorage.put(SESSION_ID, any()) }
        }
    }

    @Test
    fun certificateRepository_refreshes_certificate_when_app_becomes_in_use() = runBlockingTest {
        withTestRepository {
            currentTimeMs = CERT_INFO.refreshAt + 1
            coVerify { mockApi wasNot Called }

            appInUseFlow.value = true
            coVerify { mockApi.getCertificate(any(), any()) }
        }
    }

    @Test
    fun certificateRepository_schedules_refresh_when_app_becomes_in_use() = runBlockingTest {
        withTestRepository {
            verify { mockRefeshScheduler wasNot Called }

            appInUseFlow.value = true
            verify { mockRefeshScheduler.rescheduleAt(CERT_INFO.refreshAt) }
        }
    }

    @Test
    fun certificateRepository_does_not_schedule_refresh_when_refreshing_and_app_not_in_use() = runBlockingTest {
        currentTimeMs = CERT_INFO.refreshAt + 1

        withTestRepository {
            coVerify { mockApi.getCertificate(any(), any()) }
            verify { mockRefeshScheduler wasNot Called }
        }
    }

    @Test
    fun certificateRepository_refreshes_valid_certificate_when_plan_changes() = runBlockingTest {
        withTestRepository {
            coVerify { mockApi wasNot Called }

            infoChangeFlow.value = listOf(UserPlanManager.InfoChange.PlanChange.Upgrade)
            coVerify { mockApi.getCertificate(any(), any()) }
        }
    }

    /**
     * Create a CertificateRepository for testing and run testBlock.
     *
     * testBlock is launched in a separate coroutine that is then cancelled to avoid runtBlockingTest complaining about
     * unfinished jobs. The jobs are started by CertificateRepository to collect infinite Flows.
     */
    private fun TestCoroutineScope.withTestRepository(testBlock: suspend CoroutineScope.(CertificateRepository) -> Unit) {
        launch {
            val certificateRepository = createRepository(this)
            testBlock(certificateRepository)
        }.cancel()
    }

    private fun createRepository(scope: CoroutineScope) =
        CertificateRepository(
            scope,
            mockStorage,
            mockKeyProvider,
            mockApi,
            ::currentTimeMs,
            mockPlanManager,
            mockCurrentUser,
            mockRefeshScheduler,
            mockAppInUseMonitor
        )

    companion object {
        private const val NOW_MS = 100L

        private val SESSION_ID = SessionId("sessionId")
        private val CERT_INFO = CertInfo(
            "fake private key",
            "fake public key",
            "fake x25519",
            refreshAt = NOW_MS + 10,
            expiresAt = NOW_MS + 20,
            certificatePem = "fake certificate data"
        )
        private val CERTIFICATE_RESPONSE = CertificateResponse(
            "fake certificate data",
            refreshTime = NOW_MS + 10,
            expirationTime = NOW_MS + 200,
        )
    }
}
