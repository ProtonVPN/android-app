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
import com.protonvpn.android.vpn.MIN_CERT_REFRESH_DELAY
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
import me.proton.core.network.domain.NetworkManager
import me.proton.core.network.domain.NetworkStatus
import me.proton.core.network.domain.session.SessionId
import org.junit.Before
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

/**
 * Unit tests for certificate refresh tests.
 *
 * These tests need to be executed with instrumentation because CertificateRepository uses go libraries.
 */
@OptIn(ExperimentalCoroutinesApi::class, kotlin.time.ExperimentalTime::class)
class CertificateRefreshTests {

    private var currentTimeMs: Long = 0
    private lateinit var infoChangeFlow: MutableStateFlow<List<UserPlanManager.InfoChange>>
    private lateinit var appInUseFlow: MutableStateFlow<Boolean>
    private lateinit var networkStateFlow: MutableStateFlow<NetworkStatus>

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

    @MockK
    private lateinit var networkManager: NetworkManager

    @Before
    fun setup() {
        MockKAnnotations.init(this)

        currentTimeMs = NOW_MS
        infoChangeFlow = MutableStateFlow(emptyList())
        appInUseFlow = MutableStateFlow(false)
        networkStateFlow = MutableStateFlow(NetworkStatus.Unmetered)

        every { mockKeyProvider.generateCertInfo() } returns CertInfo("private", "public", "x25519")
        every { mockPlanManager.infoChangeFlow } returns infoChangeFlow
        every { mockAppInUseMonitor.isInUseFlow } returns appInUseFlow
        every { mockAppInUseMonitor.isInUse } answers { appInUseFlow.value }
        every { networkManager.observe() } returns networkStateFlow
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

    @Test
    fun certificateRepository_refreshes_certificate_when_network_is_available() = runBlockingTest {
        currentTimeMs = CERT_INFO.refreshAt + 1
        networkStateFlow.value = NetworkStatus.Disconnected
        withTestRepository {
            coVerify { mockApi wasNot Called }
            networkStateFlow.value = NetworkStatus.Unmetered
            coVerify { mockApi.getCertificate(any(), any()) }
        }
    }

    @Test
    fun `certificateRepository getCertificate refreshes certificate when it's expired`() = runBlockingTest {
        currentTimeMs = CERT_INFO.refreshAt - 100
        withTestRepository { repository ->
            coVerify { mockApi wasNot Called }
            currentTimeMs = CERT_INFO.expiresAt + 100
            repository.getCertificate(SESSION_ID)

            coVerify { mockApi.getCertificate(any(), any()) }
        }
    }

    @Test
    fun `error triggers reschedule`() = runBlockingTest {
        currentTimeMs = CERT_INFO.refreshAt
        appInUseFlow.value = true
        coEvery { mockApi.getCertificate(any(), any()) } returns ApiResult.Error.Timeout(true)
        withTestRepository {
            verify { mockRefeshScheduler.rescheduleAt((currentTimeMs + CERT_INFO.expiresAt) / 2) }
        }
    }

    @Test
    fun `error triggers reschedule with min delay`() = runBlockingTest {
        currentTimeMs = CERT_INFO.expiresAt - 10
        appInUseFlow.value = true
        coEvery { mockApi.getCertificate(any(), any()) } returns ApiResult.Error.Timeout(true)
        withTestRepository {
            verify { mockRefeshScheduler.rescheduleAt(currentTimeMs + MIN_CERT_REFRESH_DELAY) }
        }
    }

    @Test
    fun `retry-after error triggers reschedule`() = runBlockingTest {
        currentTimeMs = CERT_INFO.refreshAt
        appInUseFlow.value = true
        val retryAfter = 2.seconds
        coEvery { mockApi.getCertificate(any(), any()) } returns ApiResult.Error.Http(429, "", retryAfter = retryAfter)
        withTestRepository {
            verify { mockRefeshScheduler.rescheduleAt(currentTimeMs + retryAfter.inWholeMilliseconds) }
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
            mockAppInUseMonitor,
            networkManager
        )

    companion object {
        private const val NOW_MS = 100L

        private val SESSION_ID = SessionId("sessionId")
        private val CERT_INFO = CertInfo(
            "fake private key",
            "fake public key",
            "fake x25519",
            refreshAt = NOW_MS + 100_000,
            expiresAt = NOW_MS + 200_000,
            certificatePem = "fake certificate data"
        )
        private val CERTIFICATE_RESPONSE = CertificateResponse(
            "fake certificate data",
            refreshTime = NOW_MS + 100_000,
            expirationTime = NOW_MS + 2_000_000,
        )
    }
}
