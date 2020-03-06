package com.protonvpn.tests.api

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ch.protonvpn.android.test.BuildConfig
import com.protonvpn.android.api.ApiResult
import com.protonvpn.android.api.ProtonApiManager
import com.protonvpn.android.api.ProtonPrimaryApiBackend
import com.protonvpn.android.models.login.SessionListResponse
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import retrofit2.Response
import java.net.SocketTimeoutException
import java.util.*
import kotlin.math.pow

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
class ApiManagerTests {

    companion object {
        private val resultOk10 = ApiResult.Success(Response.success(SessionListResponse(10, listOf())))
        private val result400 = ApiResult.ErrorResponse(400, "")
        private val resultTimeout = ApiResult.Failure(SocketTimeoutException())
    }

    private lateinit var context: Context
    private lateinit var scope: CoroutineScope
    private lateinit var protonApiManager: ProtonApiManager

    @MockK
    private lateinit var primaryBackend: ProtonPrimaryApiBackend
    @MockK
    private lateinit var random0: Random

    @Before
    fun setup() {
        MockKAnnotations.init(this)

        context = InstrumentationRegistry.getInstrumentation().context
        scope = CoroutineScope(Dispatchers.Main)

        every { random0.nextDouble() } returns 0.0
        protonApiManager = ProtonApiManager(primaryBackend, random0)
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
}
