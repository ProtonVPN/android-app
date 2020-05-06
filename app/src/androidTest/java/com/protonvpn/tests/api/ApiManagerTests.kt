package com.protonvpn.tests.api

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.protonvpn.android.BuildConfig
import com.protonvpn.android.api.*
import com.protonvpn.android.models.config.UserData
import com.protonvpn.android.models.login.GenericResponse
import com.protonvpn.android.models.login.SessionListResponse
import com.protonvpn.android.vpn.VpnStateMonitor
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.spyk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runBlockingTest
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import retrofit2.Response
import java.net.SocketTimeoutException
import java.util.*
import java.util.concurrent.TimeoutException
import kotlin.math.pow

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
class ApiManagerTests {

    companion object {
        private const val ALT_URL_1 = "https://alt1.com/"
        private const val ALT_URL_2 = "https://alt2.com/"

        private val resultOk10 = ApiResult.Success(Response.success(SessionListResponse(10, listOf())))
        private val resultPingOk = ApiResult.Success(Response.success(GenericResponse(10)))
        private val result400 = ApiResult.ErrorResponse(400, "")
        private val resultTimeout = ApiResult.Failure(SocketTimeoutException())
    }

    private var now = 0L
    private lateinit var context: Context
    private lateinit var scope: CoroutineScope
    private lateinit var userData: UserData
    private lateinit var badDnsProvider: AlternativeApiManager.DnsOverHttpsProvider
    private lateinit var okDnsProvider: AlternativeApiManager.DnsOverHttpsProvider
    private lateinit var altApiManager: AlternativeApiManager
    private lateinit var protonApiManager: ProtonApiManager

    @MockK
    private lateinit var primaryBackend: ProtonPrimaryApiBackend
    @MockK
    private lateinit var altBackend1: ApiBackendRetrofit<ProtonVPNRetrofit>
    @MockK
    private lateinit var altBackend2: ApiBackendRetrofit<ProtonVPNRetrofit>
    @MockK
    private lateinit var monitor: VpnStateMonitor
    @MockK
    private lateinit var random0: Random

    inner class TestAlternativeApiManager : AlternativeApiManager(BuildConfig.API_DOMAIN, userData, ::now) {
        override fun createAltBackend(baseUrl: String): ApiBackendRetrofit<ProtonVPNRetrofit> =
                mapOf(ALT_URL_1 to altBackend1, ALT_URL_2 to altBackend2).getValue(baseUrl)

        override fun getDnsOverHttpsProviders(): Array<out DnsOverHttpsProvider> =
                arrayOf(badDnsProvider, okDnsProvider)
    }

    @Before
    fun setup() {
        MockKAnnotations.init(this)

        context = InstrumentationRegistry.getInstrumentation().context
        scope = CoroutineScope(Dispatchers.Main)
        userData = UserData()

        badDnsProvider = object : AlternativeApiManager.DnsOverHttpsProvider {
            override suspend fun getAlternativeBaseUrls(domain: String): List<String>? = null
        }

        okDnsProvider = object : AlternativeApiManager.DnsOverHttpsProvider {
            override suspend fun getAlternativeBaseUrls(domain: String) = listOf(ALT_URL_1, ALT_URL_2)
        }

        every { random0.nextDouble() } returns 0.0
        every { altBackend1.baseUrl } returns ALT_URL_1
        every { altBackend2.baseUrl } returns ALT_URL_2
        altApiManager = spyk(TestAlternativeApiManager())
        protonApiManager = ProtonApiManager(context, userData, altApiManager, primaryBackend, random0)
    }

    @After
    fun cleanup() {
        userData.apiUseDoH = false
    }

    @Test
    fun testCallOk() = runBlockingTest {
        coEvery { primaryBackend.call<SessionListResponse>(any()) } returns resultOk10
        Assert.assertEquals(10, protonApiManager.call { it.getSession() }.valueOrNull?.code)
    }

    @Test
    fun testCall400() = runBlockingTest {
        coEvery { primaryBackend.call<SessionListResponse>(any()) } returns result400
        Assert.assertEquals(400, (protonApiManager.call { it.getSession() } as ApiResult.ErrorResponse).httpCode)
    }

    @Test
    fun testBackoff() = runBlockingTest {
        userData.apiUseDoH = false
        coEvery { primaryBackend.call<SessionListResponse>(any()) } returns resultTimeout
        val start = currentTime
        protonApiManager.call(useBackoff = true) { it.getSession() }
        val duration = currentTime - start
        coVerify(exactly = ProtonApiManager.BACKOFF_RETRY_COUNT + 1) {
            primaryBackend.call<SessionListResponse>(any())
        }

        val expectedDuration = sumGeometricSeries(ProtonApiManager.BACKOFF_RETRY_DELAY_MS.toDouble(),
                2.0, ProtonApiManager.BACKOFF_RETRY_COUNT)
        Assert.assertEquals(expectedDuration, duration.toDouble(), expectedDuration / 20)
    }

    private fun sumGeometricSeries(start: Double, ratio: Double, n: Int) =
            start * (1 - ratio.pow(n)) / (1 - ratio)

    @Test
    fun testDohFallback() = runBlockingTest {
        userData.apiUseDoH = true
        setupResults(resultTimeout, resultTimeout, resultOk10, resultTimeout)
        Assert.assertEquals(10, protonApiManager.call { it.getSession() }.valueOrNull?.code)
        Assert.assertEquals(altBackend2, altApiManager.getActiveBackend())

        // When this time has passed, active backend should again be primary
        now += AlternativeApiManager.ALTERNATIVE_API_ACTIVE_PERIOD + 1000L
        Assert.assertEquals(null, altApiManager.getActiveBackend())
    }

    @Test
    fun testDohNotUsedOnVpnConnection() = runBlockingTest {
        every { monitor.isConnected } returns true
        protonApiManager.initVpnState(monitor)
        userData.apiUseDoH = true
        setupResults(resultTimeout, resultOk10, resultOk10, resultTimeout)
        Assert.assertEquals(resultTimeout, protonApiManager.call { it.getSession() })
    }

    @Test
    fun testDohNotBlockingError() = runBlockingTest {
        userData.apiUseDoH = true
        setupResults(result400, resultOk10, resultOk10, resultTimeout)
        Assert.assertEquals(result400, protonApiManager.call { it.getSession() })
    }

    @Test
    fun testDohTimeout() = runBlockingTest {
        userData.apiUseDoH = true
        coEvery { primaryBackend.call<SessionListResponse>(any()) } returns resultTimeout
        coEvery { primaryBackend.ping() } returns resultTimeout
        coEvery { altBackend1.call<SessionListResponse>(any()) } returns resultTimeout
        coEvery { altBackend2.call<SessionListResponse>(any()) } coAnswers {
            delay(ProtonApiManager.DOH_TIMEOUT + 1000L)
            resultOk10
        }
        val result = protonApiManager.call { it.getSession() }
        Assert.assertTrue((result as ApiResult.Failure).exception is TimeoutException)
    }

    @Test
    fun testDohPingOk() = runBlockingTest {
        userData.apiUseDoH = true
        setupResults(resultTimeout, resultOk10, resultOk10, resultPingOk)
        val result = protonApiManager.call { it.getSession() }
        Assert.assertEquals(resultTimeout, result)

        coVerify(exactly = 1) { primaryBackend.ping() }
        coVerify(exactly = 1) { altApiManager.refreshDomains() }
        coVerify(exactly = 0) { altApiManager.callWithAlternatives<SessionListResponse>(any(), any()) }
    }

    @Test
    fun testDohRefreshAndCache() = runBlockingTest {
        setupResults(resultTimeout, resultOk10, resultOk10, resultTimeout)
        Assert.assertNull(altApiManager.callWithAlternatives(context) { it.getSession() })
        altApiManager.refreshDomains()
        Assert.assertEquals(resultOk10, altApiManager.callWithAlternatives(context) { it.getSession() })

        val newAltManager = TestAlternativeApiManager()
        // no need to refresh, should take it from userData
        Assert.assertEquals(resultOk10, newAltManager.callWithAlternatives(context) { it.getSession() })
    }

    private fun setupResults(
        primary: ApiResult<SessionListResponse>,
        alt1: ApiResult<SessionListResponse>,
        alt2: ApiResult<SessionListResponse>,
        ping: ApiResult<GenericResponse>
    ) {
        coEvery { primaryBackend.call<SessionListResponse>(any()) } returns primary
        coEvery { altBackend1.call<SessionListResponse>(any()) } returns alt1
        coEvery { altBackend2.call<SessionListResponse>(any()) } returns alt2
        coEvery { primaryBackend.ping() } returns ping
    }
}
