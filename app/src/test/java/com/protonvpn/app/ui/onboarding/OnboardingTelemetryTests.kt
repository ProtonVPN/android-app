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

package com.protonvpn.app.ui.onboarding

import android.app.Activity
import com.protonvpn.android.appconfig.AppFeaturesPrefs
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.telemetry.CommonDimensions
import com.protonvpn.android.telemetry.Telemetry
import com.protonvpn.android.ui.ForegroundActivityTracker
import com.protonvpn.android.ui.home.ServerListUpdaterPrefs
import com.protonvpn.android.ui.main.MobileMainActivity
import com.protonvpn.android.ui.onboarding.OnboardingActivity
import com.protonvpn.android.ui.onboarding.OnboardingTelemetry
import com.protonvpn.android.vpn.VpnStateMonitor
import com.protonvpn.test.shared.MockSharedPreferencesProvider
import com.protonvpn.test.shared.TestCurrentUserProvider
import com.protonvpn.test.shared.TestDispatcherProvider
import com.protonvpn.test.shared.TestUser
import io.mockk.Called
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import me.proton.core.auth.presentation.ui.signup.SignupActivity
import org.junit.Before
import org.junit.Test

private const val GROUP = "vpn.any.onboarding"

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingTelemetryTests {

    @MockK
    private lateinit var mockForegroundActivityTracker: ForegroundActivityTracker
    @MockK
    private lateinit var mockTelemetry: Telemetry

    private lateinit var appFeaturesPrefs: AppFeaturesPrefs
    private lateinit var currentUser: CurrentUser
    private lateinit var foregroundActivityFlow: MutableStateFlow<Activity?>
    private lateinit var serverListPrefs: ServerListUpdaterPrefs
    private lateinit var testUserProvider: TestCurrentUserProvider
    private lateinit var testDispatcher: TestDispatcher
    private lateinit var testScope: TestScope
    private lateinit var vpnStateMonitor: VpnStateMonitor

    @Before
    fun setup() {
        MockKAnnotations.init(this)

        testDispatcher = StandardTestDispatcher()
        testScope = TestScope(testDispatcher)

        foregroundActivityFlow = MutableStateFlow(null)
        every { mockForegroundActivityTracker.foregroundActivityFlow } returns foregroundActivityFlow
        every { mockTelemetry.event(GROUP, any(), any(), any(), any()) } just runs

        appFeaturesPrefs = AppFeaturesPrefs(MockSharedPreferencesProvider())
        testUserProvider = TestCurrentUserProvider(vpnUser = null)
        currentUser = CurrentUser(testScope.backgroundScope, testUserProvider)
        serverListPrefs =  ServerListUpdaterPrefs(MockSharedPreferencesProvider())
        vpnStateMonitor = VpnStateMonitor()
    }

    @Test
    fun `first_launch reported when OnboardingTelemetry started for the first time`() = testScope.runTest {
        val telemetry1 = createTelemetry()
        val telemetry2 = createTelemetry()
        telemetry1.onAppStart()
        telemetry2.onAppStart()
        runCurrent()

        verify(exactly = 1) { mockTelemetry.event(GROUP, "first_launch", any(), any(), true) }
    }

    @Test
    fun `signup_start reported only when SignupActivity goes foreground`() = testScope.runTest {
        val telemetry = createTelemetry()

        repeat(3) {
            foregroundActivityFlow.value = mockk<SignupActivity>()
            runCurrent()
            foregroundActivityFlow.value = null
            runCurrent()
            foregroundActivityFlow.value = mockk<MobileMainActivity>()
            runCurrent()
        }

        verify(exactly = 1) { mockTelemetry.event(GROUP, "signup_start", any(), any(), true) }
    }

    @Test
    fun `onboarding_start reported when OnboardingActivity goes foreground`() = testScope.runTest {
        val telemetry = createTelemetry()
        foregroundActivityFlow.value = mockk<OnboardingActivity>()
        runCurrent()

        verify(exactly = 1) { mockTelemetry.event(GROUP, "onboarding_start", any(), any(), true) }
    }

    @Test
    fun `payment_done reported`() = testScope.runTest {
        val telemetry = createTelemetry()
        telemetry.onOnboardingPaymentSuccess("new plan")
        runCurrent()

        val expectedDimensions = mapOf("user_plan" to "new plan", "user_country" to "n/a")
        verify(exactly = 1) { mockTelemetry.event(GROUP, "payment_done", any(), expectedDimensions, true) }
    }

    @Test
    fun `first_connect reported on first connection attempt`() = testScope.runTest {
        val telemetry = createTelemetry()

        repeat(3) {
            vpnStateMonitor.newSessionEvent.emit(Unit)
            runCurrent()
        }

        verify(exactly = 1) { mockTelemetry.event(GROUP, "first_connection", any(), any(), true) }
    }

    @Test
    fun `dimensions are set`() = testScope.runTest {
        serverListPrefs.lastKnownCountry = "UK"
        val telemetry = createTelemetry()
        telemetry.onAppStart()
        runCurrent()

        testUserProvider.vpnUser = TestUser.freeUser.vpnUser
        telemetry.onOnboardingPaymentSuccess("vpnPlus")
        runCurrent()

        verify(exactly = 1) {
            mockTelemetry.event(GROUP, "first_launch", emptyMap(), mapOf("user_country" to "UK"), true)
        }
        verify(exactly = 1) {
            val dimensions =  mapOf("user_country" to "UK", "user_plan" to "vpnPlus")
            mockTelemetry.event(GROUP, "payment_done", emptyMap(), dimensions, true)
        }
    }

    @Test
    fun `app update disables onboarding telemetry`() = testScope.runTest {
        val telemetry = createTelemetry()
        telemetry.onAppUpdate()
        runCurrent()
        telemetry.onAppStart()
        runCurrent()

        verify { mockTelemetry wasNot Called }
    }

    private fun createTelemetry() = OnboardingTelemetry(
        testScope.backgroundScope,
        TestDispatcherProvider(testDispatcher),
        mockTelemetry,
        mockForegroundActivityTracker,
        vpnStateMonitor,
        currentUser,
        CommonDimensions(vpnStateMonitor, serverListPrefs),
        appFeaturesPrefs
    )

}
