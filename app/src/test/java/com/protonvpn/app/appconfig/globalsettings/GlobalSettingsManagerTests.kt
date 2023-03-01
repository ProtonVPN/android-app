/*
 * Copyright (c) 2023. Proton AG
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

package com.protonvpn.app.appconfig.globalsettings

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.protonvpn.android.api.ProtonApiRetroFit
import com.protonvpn.android.appconfig.globalsettings.GlobalSettingUpdateScheduler
import com.protonvpn.android.appconfig.globalsettings.GlobalSettingsManager
import com.protonvpn.android.appconfig.globalsettings.GlobalSettingsPrefs
import com.protonvpn.android.appconfig.globalsettings.GlobalSettingsResponse
import com.protonvpn.android.appconfig.globalsettings.GlobalUserSettings
import com.protonvpn.android.models.config.UserData
import com.protonvpn.android.utils.Storage
import com.protonvpn.test.shared.MockSharedPreference
import com.protonvpn.test.shared.MockSharedPreferencesProvider
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import me.proton.core.network.domain.ApiResult
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GlobalSettingsManagerTests {

    @get:Rule
    var instantExecutorRule = InstantTaskExecutorRule()

    @RelaxedMockK
    private lateinit var mockApi: ProtonApiRetroFit
    @RelaxedMockK
    private lateinit var mockGlobalSettingUpdateScheduler: GlobalSettingUpdateScheduler

    private lateinit var globalSettingsPrefs: GlobalSettingsPrefs
    private lateinit var userData: UserData
    private lateinit var testScope: TestScope
    private lateinit var globalSettingsManager: GlobalSettingsManager

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        val testDispatcher = UnconfinedTestDispatcher()
        Dispatchers.setMain(testDispatcher)
        testScope = TestScope(testDispatcher)

        Storage.setPreferences(MockSharedPreference())
        userData = UserData.create(null)
        globalSettingsPrefs = GlobalSettingsPrefs(MockSharedPreferencesProvider())

        globalSettingsManager = GlobalSettingsManager(
            mockk(relaxed = true),
            testScope.backgroundScope,
            mockApi,
            globalSettingsPrefs,
            userData,
            mockGlobalSettingUpdateScheduler
        )
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `enabling telemetry updates global telemetry setting`() = testScope.runTest {
        userData.telemetryEnabled = true
        assertTrue(globalSettingsPrefs.telemetryEnabled)
        coVerify { mockGlobalSettingUpdateScheduler.updateRemoteTelemetry(true) }
    }

    @Test
    fun `disabling telemetry doesn't affect global setting`() = testScope.runTest {
        globalSettingsPrefs.telemetryEnabled = true
        userData.telemetryEnabled = true

        userData.telemetryEnabled = false
        assertTrue(globalSettingsPrefs.telemetryEnabled)
        coVerify(exactly = 0) { mockGlobalSettingUpdateScheduler.updateRemoteTelemetry(false) }
    }

    @Test
    fun `enabling global telemetry setting doesn't change the local one`() = testScope.runTest {
        val response = GlobalSettingsResponse(GlobalUserSettings(telemetryEnabled = true))
        coEvery { mockApi.getGlobalSettings() } returns ApiResult.Success(response)
        globalSettingsManager.refresh()

        assertTrue(globalSettingsPrefs.telemetryEnabled)
        assertFalse(userData.telemetryEnabled)
    }

    @Test
    fun `disabling global telemetry setting disables the local one`() = testScope.runTest {
        globalSettingsPrefs.telemetryEnabled = true
        userData.telemetryEnabled = true

        val response = GlobalSettingsResponse(GlobalUserSettings(telemetryEnabled = false))
        coEvery { mockApi.getGlobalSettings() } returns ApiResult.Success(response)
        globalSettingsManager.refresh()

        assertFalse(globalSettingsPrefs.telemetryEnabled)
        assertFalse(userData.telemetryEnabled)
    }

    @Test
    fun `when global setting update returns a different value it is applied`() = testScope.runTest {
        val response = GlobalSettingsResponse(GlobalUserSettings(telemetryEnabled = false))
        coEvery { mockApi.putTelemetryGlobalSetting(true) } returns ApiResult.Success(response)

        userData.telemetryEnabled = true
        assertTrue(globalSettingsPrefs.telemetryEnabled)
        coVerify { mockGlobalSettingUpdateScheduler.updateRemoteTelemetry(true) }

        // Backend responds with telemetry being false despite the update.
        globalSettingsManager.uploadGlobalTelemetrySetting(true)

        assertFalse(userData.telemetryEnabled)
        assertFalse(globalSettingsPrefs.telemetryEnabled)
        // No additional calls to uploadRemoteTelemetry.
        coVerify(exactly = 1) { mockGlobalSettingUpdateScheduler.updateRemoteTelemetry(any()) }
    }
}
