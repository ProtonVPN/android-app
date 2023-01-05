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

package com.protonvpn.app.telemetry

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.protonvpn.android.api.ProtonApiRetroFit
import com.protonvpn.android.appconfig.AppConfig
import com.protonvpn.android.appconfig.FeatureFlags
import com.protonvpn.android.models.config.UserData
import com.protonvpn.android.models.login.GenericResponse
import com.protonvpn.android.telemetry.StatsBody
import com.protonvpn.android.telemetry.Telemetry
import com.protonvpn.android.utils.Storage
import com.protonvpn.test.shared.MockSharedPreference
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import me.proton.core.network.domain.ApiResult
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TelemetryTests {

    @get:Rule
    val rule = InstantTaskExecutorRule()

    @MockK
    private lateinit var mockApi: ProtonApiRetroFit

    @MockK
    private lateinit var mockAppConfig: AppConfig

    private lateinit var userData: UserData
    private lateinit var testScope: TestScope

    private lateinit var telemetry: Telemetry

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        Storage.setPreferences(MockSharedPreference())
        userData = UserData.create()
        testScope = TestScope(UnconfinedTestDispatcher())

        userData.telemetryEnabled = true
        every { mockAppConfig.getFeatureFlags() } returns FeatureFlags(telemetry = true)
        coEvery { mockApi.postStats(any(), any(), any(), any()) } returns ApiResult.Success(GenericResponse(1000))

        telemetry = Telemetry(testScope, mockApi, mockAppConfig, userData)
    }

    @Test
    fun `event serialization to JSON`() {
        val event = StatsBody(
            "vpn.any.connection",
            "event",
            mapOf("some value" to 1000L),
            mapOf("dimension1" to "value1", "dimension2" to "value2")
        )
        val jsonString = Json.encodeToString(event)
        assertEquals(
            """{"MeasurementGroup":"vpn.any.connection","Event":"event","Values":{"some value":1000},"Dimensions":{"dimension1":"value1","dimension2":"value2"}}""",
            jsonString
        )
    }

    @Test
    fun `event sent to API`() = testScope.runTest {
        telemetry.event(MEASUREMENT_GROUP, EVENT_NAME, VALUES, DIMENSIONS)
        coVerify {
            mockApi.postStats(MEASUREMENT_GROUP, EVENT_NAME, VALUES, DIMENSIONS)
        }
    }

    @Test
    fun `when feature disabled then nothing is reported`() = testScope.runTest {
        every { mockAppConfig.getFeatureFlags() } returns FeatureFlags(telemetry = false)
        telemetry.event(MEASUREMENT_GROUP, EVENT_NAME, VALUES, DIMENSIONS)
        coVerify(exactly = 0) {
            mockApi.postStats(any(), any(), any(), any())
        }
    }

    @Test
    fun `when user setting disabled then nothing is reported`() = testScope.runTest {
        userData.telemetryEnabled = false
        telemetry.event(MEASUREMENT_GROUP, EVENT_NAME, VALUES, DIMENSIONS)
        coVerify(exactly = 0) {
            mockApi.postStats(any(), any(), any(), any())
        }
    }

    companion object {
        private const val MEASUREMENT_GROUP = "measurement group"
        private const val EVENT_NAME = "event name"
        private val VALUES = mapOf("value" to 1L)
        private val DIMENSIONS = mapOf("dimension1" to "value1", "dimension2" to "value2")
    }
}
