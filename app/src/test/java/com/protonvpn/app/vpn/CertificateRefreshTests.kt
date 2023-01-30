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
import com.protonvpn.android.appconfig.periodicupdates.PeriodicUpdateManager
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.models.vpn.CertificateResponse
import com.protonvpn.android.utils.UserPlanManager
import com.protonvpn.android.vpn.CertInfo
import com.protonvpn.android.vpn.CertificateKeyProvider
import com.protonvpn.android.vpn.CertificateRepository
import com.protonvpn.android.vpn.CertificateStorage
import com.protonvpn.android.vpn.MIN_CERT_REFRESH_DELAY
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.RelaxedMockK
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import me.proton.core.network.domain.ApiResult
import me.proton.core.network.domain.session.SessionId
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

/**
 * Unit tests for certificate refresh tests.
 */
@OptIn(ExperimentalCoroutinesApi::class, kotlin.time.ExperimentalTime::class)
class CertificateRefreshTests {

    private var currentTimeMs: Long = 0
    private lateinit var infoChangeFlow: MutableSharedFlow<List<UserPlanManager.InfoChange>>

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
    private lateinit var mockPeriodicUpdateManager: PeriodicUpdateManager

    @Before
    fun setup() {
        MockKAnnotations.init(this)

        currentTimeMs = NOW_MS
        infoChangeFlow = MutableSharedFlow(extraBufferCapacity = 1)

        val certs = mutableMapOf<SessionId, CertInfo>()
        coEvery { mockStorage.get(any()) } answers { certs[firstArg()] }
        coEvery { mockStorage.put(any(), any()) } answers { certs[firstArg()] = secondArg() }
        coEvery { mockStorage.remove(any()) } answers { certs.remove(firstArg()) }
        runBlocking { mockStorage.put(SESSION_ID, CERT_INFO) }

        every { mockKeyProvider.generateCertInfo() } returns CertInfo("private", "public", "x25519")
        every { mockPlanManager.infoChangeFlow } returns infoChangeFlow
        coEvery { mockApi.getCertificate(any(), any()) } returns ApiResult.Success(CERTIFICATE_RESPONSE)
        coEvery { mockCurrentUser.sessionId() } returns SESSION_ID
    }

    @Test
    fun `certificateRepository getCertificate refreshes certificate when it's expired`() = runTest(UnconfinedTestDispatcher()) {
        val repository = createRepository(backgroundScope)
        coEvery {
            mockPeriodicUpdateManager.executeNow<SessionId, CertificateRepository.CertificateResult>(any(), SESSION_ID)
        } returns
            CertificateRepository.CertificateResult.Success(CERTIFICATE_RESPONSE.certificate, CERT_INFO.privateKeyPem)
        currentTimeMs = CERT_INFO.expiresAt + 100
        repository.getCertificate(SESSION_ID)

        coVerify {
            mockPeriodicUpdateManager.executeNow<SessionId, Any>(match { it.id == "vpn_certificate" }, SESSION_ID)
        }
    }

    @Test
    fun `certificateRepository clears and refreshes valid certificate when plan changes`() = runTest(UnconfinedTestDispatcher()) {
        createRepository(backgroundScope)
        coEvery {
            mockPeriodicUpdateManager.executeNow<SessionId, CertificateRepository.CertificateResult>(any(), SESSION_ID)
        } returns
            CertificateRepository.CertificateResult.Success(CERTIFICATE_RESPONSE.certificate, CERT_INFO.privateKeyPem)
        coVerify(exactly = 0) { mockPeriodicUpdateManager.executeNow<Any, Any>(any(), any()) }

        infoChangeFlow.emit(listOf(UserPlanManager.InfoChange.PlanChange.Upgrade))

        val clearedCertInfo = CertInfo(
            CERT_INFO.privateKeyPem,
            CERT_INFO.publicKeyPem,
            CERT_INFO.x25519Base64
        )
        coVerify {
            mockPeriodicUpdateManager.executeNow<SessionId, Any>(match { it.id == "vpn_certificate" }, SESSION_ID)
        }
        coVerify { mockStorage.put(SESSION_ID, clearedCertInfo) }
    }

    @Test
    fun `updateCertificateInternal computes next update time at cert refresh time`() = runTest {
        val repository = createRepository(backgroundScope)
        val result = repository.updateCertificateInternal(SESSION_ID)
        assertEquals(CERT_INFO.refreshAt, result.nextCallDelayOverride!! + currentTimeMs)
    }

    @Test
    fun `updateCertificateInternal computes short next update delay on refresh error`() = runTest {
        val repository = createRepository(backgroundScope)
        currentTimeMs = CERT_INFO.refreshAt
        coEvery { mockApi.getCertificate(any(), any()) } returns ApiResult.Error.Timeout(true)
        val result = repository.updateCertificateInternal(SESSION_ID)
        assertEquals((currentTimeMs + CERT_INFO.expiresAt) / 2, result.nextCallDelayOverride!! + currentTimeMs)
    }

    @Test
    fun `updateCertificateInternal computes next update with min delay on refresh error`() = runTest {
        val repository = createRepository(backgroundScope)
        currentTimeMs = CERT_INFO.expiresAt - 10
        coEvery { mockApi.getCertificate(any(), any()) } returns ApiResult.Error.Timeout(true)
        val result = repository.updateCertificateInternal(SESSION_ID)
        assertEquals(MIN_CERT_REFRESH_DELAY, result.nextCallDelayOverride!!)
    }

    @Test
    fun `updateCertificateInternal respects retry-after`() = runTest(UnconfinedTestDispatcher()) {
        val repository = createRepository(backgroundScope)
        currentTimeMs = CERT_INFO.refreshAt
        val retryAfter = 2.seconds
        coEvery { mockApi.getCertificate(any(), any()) } returns ApiResult.Error.Http(429, "", retryAfter = retryAfter)
        val result = repository.updateCertificateInternal(SESSION_ID)
        assertEquals(retryAfter.inWholeMilliseconds, result.nextCallDelayOverride!!)
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
            mockPeriodicUpdateManager,
            flowOf(true)
        )

    companion object {
        private const val NOW_MS = 1000L

        private val SESSION_ID = SessionId("sessionId")
        private val CERT_INFO = CertInfo(
            "fake private key",
            "fake public key",
            "fake x25519",
            refreshAt = NOW_MS + 100_000,
            expiresAt = NOW_MS + 200_000,
            certificatePem = "fake certificate data"
        )
        private const val UPDATED_CERT = "updated fake certificate data"
        private val CERTIFICATE_RESPONSE = CertificateResponse(
            UPDATED_CERT,
            refreshTime = (NOW_MS + 100_000) / 1000,
            expirationTime = (NOW_MS + 2_000_000) / 1000,
        )
    }
}
