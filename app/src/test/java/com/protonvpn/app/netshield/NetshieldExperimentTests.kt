/*
 * Copyright (c) 2023 Proton Technologies AG
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
package com.protonvpn.app.netshield

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.protonvpn.android.api.ProtonApiRetroFit
import com.protonvpn.android.auth.data.VpnUser
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.netshield.NetShieldProtocol
import com.protonvpn.android.models.config.UserData
import com.protonvpn.android.netshield.Experiment
import com.protonvpn.android.netshield.ExperimentResponse
import com.protonvpn.android.netshield.NetShieldExperiment
import com.protonvpn.android.netshield.NetShieldExperimentPrefs
import com.protonvpn.android.netshield.SentryRecorder
import com.protonvpn.android.utils.Storage
import com.protonvpn.test.shared.MockSharedPreference
import com.protonvpn.test.shared.MockSharedPreferencesProvider
import com.protonvpn.test.shared.mockVpnUser
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import me.proton.core.network.domain.ApiResult
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class NetshieldExperimentTests {

    @get:Rule
    val instantTaskExecutor = InstantTaskExecutorRule()

    @MockK
    private lateinit var mockApi: ProtonApiRetroFit
    @RelaxedMockK
    private lateinit var sentryRecorder: SentryRecorder
    @MockK
    private lateinit var mockCurrentUser: CurrentUser
    @MockK
    private lateinit var vpnUser: VpnUser
    private lateinit var userData: UserData
    private lateinit var testScope: TestScope
    private lateinit var netShieldExperimentPrefs: NetShieldExperimentPrefs
    private lateinit var netShieldExperiment: NetShieldExperiment

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        val testDispatcher = UnconfinedTestDispatcher()
        testScope = TestScope(testDispatcher)
        Storage.setPreferences(MockSharedPreference())
        userData = UserData.create()
        mockCurrentUser.mockVpnUser { vpnUser }
        every { vpnUser.isUserPlusOrAbove } returns true
        every { vpnUser.isFreeUser } returns false
        netShieldExperimentPrefs = NetShieldExperimentPrefs(MockSharedPreferencesProvider())
        netShieldExperiment =
            NetShieldExperiment(mockApi, userData, netShieldExperimentPrefs, sentryRecorder, mockCurrentUser)
    }

    @Test
    fun `experiment quits succesfully if already initialized`() = testScope.runTest {
        startExperimentWith(NetShieldExperiment.ExperimentValue.EXPERIMENT_GROUP_F2)
        startExperimentWith(NetShieldExperiment.ExperimentValue.QUIT_EXPERIMENT)
        userData.setNetShieldProtocol(NetShieldProtocol.ENABLED)
        // Only start event for F2 should be sent
        coVerify(exactly = 1) { sentryRecorder.sendEvent(any()) }
    }

    @Test
    fun `user change of netshield sends sentry event and ends experiment`() = testScope.runTest {
        startExperimentWith(NetShieldExperiment.ExperimentValue.EXPERIMENT_GROUP_F2)
        assertEquals(NetShieldProtocol.ENABLED_EXTENDED, userData.getNetShieldProtocol(mockk(relaxed = true)))

        // User overwrite sends event and exits the experiment
        userData.setNetShieldProtocol(NetShieldProtocol.ENABLED)
        coVerify(exactly = 2) { sentryRecorder.sendEvent(any()) }
        assertEquals(NetShieldProtocol.ENABLED, userData.getNetShieldProtocol(mockk(relaxed = true)))
        assertEquals(true, netShieldExperimentPrefs.experimentEnded)
    }

    @Test
    fun `ended experiment does not trigger sentry events for changes`() = testScope.runTest {
        startExperimentWith(NetShieldExperiment.ExperimentValue.QUIT_EXPERIMENT)
        userData.setNetShieldProtocol(NetShieldProtocol.ENABLED)
        coVerify(exactly = 0) { sentryRecorder.sendEvent(any()) }
    }

    @Test
    fun `only Plus users call experiment API`() = testScope.runTest {
        every { vpnUser.isUserPlusOrAbove } returns false
        startExperimentWith(NetShieldExperiment.ExperimentValue.EXPERIMENT_GROUP_F1)
        coVerify(exactly = 0) { mockApi.getExperiment(any()) }
    }

    @Test
    fun `api is not called anymore if experiment end is received`() = testScope.runTest {
        startExperimentWith(NetShieldExperiment.ExperimentValue.QUIT_EXPERIMENT)
        coVerify(exactly = 1) { mockApi.getExperiment(any()) }
        netShieldExperiment.fetchExperiment()
        coVerify(exactly = 1) { mockApi.getExperiment(any()) }
        assertEquals(true, netShieldExperimentPrefs.experimentEnded)
    }

    @Test
    fun `netshield can only be overriden once with experiment`() = testScope.runTest {
        startExperimentWith(NetShieldExperiment.ExperimentValue.EXPERIMENT_GROUP_F2)
        assertEquals(NetShieldProtocol.ENABLED_EXTENDED, userData.getNetShieldProtocol(mockk(relaxed = true)))

        // F1 value will be ignored if experiment is already initialized
        startExperimentWith(NetShieldExperiment.ExperimentValue.EXPERIMENT_GROUP_F1)
        assertEquals(NetShieldProtocol.ENABLED_EXTENDED, userData.getNetShieldProtocol(mockk(relaxed = true)))
    }

    @Test
    fun `if netshield value match dont conduct experiment and quit`() = testScope.runTest {
        userData.setNetShieldProtocol(NetShieldProtocol.ENABLED)

        startExperimentWith(NetShieldExperiment.ExperimentValue.EXPERIMENT_GROUP_F1)
        coVerify(exactly = 0) { sentryRecorder.sendEvent(any()) }
        assertEquals(true, netShieldExperimentPrefs.experimentEnded)
    }

    private suspend fun startExperimentWith(state: NetShieldExperiment.ExperimentValue) {
        val experimentState = NetShieldExperiment.ExperimentState.values().find { it.value == state }!!
        coEvery { mockApi.getExperiment(any()) } returns ApiResult.Success(ExperimentResponse(1000, Experiment(experimentState)))
        netShieldExperiment.fetchExperiment()
    }
}