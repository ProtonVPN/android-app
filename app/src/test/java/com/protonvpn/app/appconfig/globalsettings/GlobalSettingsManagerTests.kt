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
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.auth.usecase.IsCredentiallessUser
import com.protonvpn.android.settings.data.CurrentUserLocalSettingsManager
import com.protonvpn.android.settings.data.LocalUserSettingsStoreProvider
import com.protonvpn.android.tv.IsTvCheck
import com.protonvpn.test.shared.InMemoryDataStoreFactory
import com.protonvpn.test.shared.MockSharedPreferencesProvider
import com.protonvpn.test.shared.TestCurrentUserProvider
import com.protonvpn.test.shared.TestUser
import io.mockk.Called
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import me.proton.core.network.domain.ApiResult
import me.proton.core.network.domain.session.SessionId
import me.proton.core.usersettings.domain.usecase.GetUserSettings
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Ignore
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
    @MockK
    private lateinit var mockIsTvCheck: IsTvCheck
    @MockK
    private lateinit var mockGetUserSettings: GetUserSettings
    @MockK
    private lateinit var mockIsCredentiallessUser: IsCredentiallessUser

    private lateinit var currentUser: CurrentUser
    private lateinit var testUserProvider: TestCurrentUserProvider
    private lateinit var globalSettingsPrefs: GlobalSettingsPrefs
    private lateinit var testScope: TestScope
    private lateinit var userSettingsManager: CurrentUserLocalSettingsManager

    private lateinit var globalSettingsManager: GlobalSettingsManager

    private val user1 = TestUser.plusUser.vpnUser
    private val user2 = TestUser.freeUser.vpnUser

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        val testDispatcher = UnconfinedTestDispatcher()
        Dispatchers.setMain(testDispatcher)
        testScope = TestScope(testDispatcher)

        testUserProvider = TestCurrentUserProvider(user1)
        currentUser = CurrentUser(testScope.backgroundScope, testUserProvider)
        userSettingsManager = CurrentUserLocalSettingsManager(
            LocalUserSettingsStoreProvider(InMemoryDataStoreFactory())
        )

        globalSettingsPrefs = GlobalSettingsPrefs(MockSharedPreferencesProvider())
        every { mockIsTvCheck.invoke() } returns false
        coEvery { mockIsCredentiallessUser.invoke(any()) } returns false

        globalSettingsManager = GlobalSettingsManager(
            testScope.backgroundScope,
            currentUser,
            mockApi,
            globalSettingsPrefs,
            userSettingsManager,
            mockIsTvCheck,
            mockGlobalSettingUpdateScheduler,
            mockGetUserSettings,
            mockIsCredentiallessUser,
        )
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `enabling telemetry updates global telemetry setting`() = testScope.runTest {
        globalSettingsPrefs.telemetryEnabled = false
        userSettingsManager.updateTelemetry(true)
        assertTrue(globalSettingsPrefs.telemetryEnabled)
        coVerify { mockGlobalSettingUpdateScheduler.updateRemoteTelemetry(true) }
    }

    @Test
    fun `disabling telemetry doesn't affect global setting`() = testScope.runTest {
        globalSettingsPrefs.telemetryEnabled = true
        userSettingsManager.updateTelemetry(true)

        userSettingsManager.updateTelemetry(false)
        assertTrue(globalSettingsPrefs.telemetryEnabled)
        coVerify(exactly = 0) { mockGlobalSettingUpdateScheduler.updateRemoteTelemetry(false) }
    }

    @Test
    fun `enabling global telemetry setting doesn't change the local one`() = testScope.runTest {
        globalSettingsPrefs.telemetryEnabled = false
        coEvery { mockGetUserSettings(any<SessionId>(), any()) } returns mockk {
            every { telemetry } returns true
        }
        globalSettingsManager.refresh(user1.userId, user1.sessionId)

        assertTrue(globalSettingsPrefs.telemetryEnabled)
        assertFalse(userSettingsManager.rawCurrentUserSettingsFlow.first().telemetry)
    }

    @Test
    fun `disabling global telemetry setting disables the local one`() = testScope.runTest {
        globalSettingsPrefs.telemetryEnabled = true
        userSettingsManager.updateTelemetry(true)

        coEvery { mockGetUserSettings(any<SessionId>(), any()) } returns mockk {
            every { telemetry } returns false
        }
        globalSettingsManager.refresh(user1.userId, user1.sessionId)

        assertFalse(globalSettingsPrefs.telemetryEnabled)
        assertFalse(userSettingsManager.rawCurrentUserSettingsFlow.first().telemetry)
    }

    @Test
    fun `when global setting update returns a different value it is applied`() = testScope.runTest {
        val response = GlobalSettingsResponse(GlobalUserSettings(telemetryEnabled = false))
        coEvery { mockApi.putTelemetryGlobalSetting(true) } returns ApiResult.Success(response)

        globalSettingsPrefs.telemetryEnabled = false
        userSettingsManager.updateTelemetry(true)
        assertTrue(globalSettingsPrefs.telemetryEnabled)
        coVerify { mockGlobalSettingUpdateScheduler.updateRemoteTelemetry(true) }

        // Backend responds with telemetry being false despite the update.
        globalSettingsManager.uploadGlobalTelemetrySetting(true)

        assertFalse(userSettingsManager.rawCurrentUserSettingsFlow.first().telemetry)
        assertFalse(globalSettingsPrefs.telemetryEnabled)
        // No additional calls to uploadRemoteTelemetry.
        coVerify(exactly = 1) { mockGlobalSettingUpdateScheduler.updateRemoteTelemetry(any()) }
    }

    @Ignore("VPNAND-1381")
    @Test
    fun `when new user logs in then global telemetry is not updated`() = testScope.runTest {
        userSettingsManager.getRawUserSettingsStore(user1).updateData { it.copy(telemetry = false) }
        userSettingsManager.getRawUserSettingsStore(user2).updateData { it.copy(telemetry = true) }
        globalSettingsPrefs.telemetryEnabled = false
        assertFalse(userSettingsManager.rawCurrentUserSettingsFlow.first().telemetry)

        testUserProvider.vpnUser = user2

        assertTrue(userSettingsManager.rawCurrentUserSettingsFlow.first().telemetry)
        assertFalse(globalSettingsPrefs.telemetryEnabled)
        coVerify(exactly = 0) { mockGlobalSettingUpdateScheduler.updateRemoteTelemetry(true) }
    }

    @Test
    fun `when user is credentialless don't fetch nor set global settings`() = testScope.runTest {
        // Given:
        coEvery { mockIsCredentiallessUser.invoke(any()) } returns true
        globalSettingsPrefs.telemetryEnabled = false
        userSettingsManager.getRawUserSettingsStore(user1).updateData { it.copy(telemetry = false) }

        // When
        userSettingsManager.getRawUserSettingsStore(user2).updateData { it.copy(telemetry = true) }
        globalSettingsManager.refresh(user2.userId, user2.sessionId)

        // Then
        coVerify(exactly = 0) { mockGlobalSettingUpdateScheduler.updateRemoteTelemetry(any()) }
        coVerify { mockGetUserSettings wasNot Called }
    }
}
