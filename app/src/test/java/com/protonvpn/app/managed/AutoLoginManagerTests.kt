/*
 * Copyright (c) 2024 Proton AG
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

@file:OptIn(ExperimentalCoroutinesApi::class)

package com.protonvpn.app.managed

import android.app.Activity
import com.protonvpn.android.R
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.auth.usecase.Logout
import com.protonvpn.android.managed.AutoLoginConfig
import com.protonvpn.android.managed.AutoLoginManager
import com.protonvpn.android.managed.AutoLoginState
import com.protonvpn.android.managed.ManagedConfig
import com.protonvpn.android.managed.ResetUiForAutoLogin
import com.protonvpn.android.managed.usecase.AutoLogin
import com.protonvpn.android.notifications.NotificationHelper
import com.protonvpn.android.ui.ForegroundActivityTracker
import com.protonvpn.test.shared.TestCurrentUserProvider
import com.protonvpn.test.shared.createAccountUser
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import me.proton.core.domain.entity.UserId
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class AutoLoginManagerTests {

    @MockK
    lateinit var notificationHelper: NotificationHelper

    @RelaxedMockK
    lateinit var resetUiForAutoLogin: ResetUiForAutoLogin

    @RelaxedMockK
    lateinit var logout: Logout

    private lateinit var scope: TestScope
    private lateinit var configFlow: MutableStateFlow<AutoLoginConfig?>
    private lateinit var autoLogin: FakeAutoLogin
    private lateinit var testCurrentUserProvider: TestCurrentUserProvider
    private lateinit var foregroundActivityFlow: MutableStateFlow<Activity?>
    private lateinit var foregroundActivityTracker: ForegroundActivityTracker

    private lateinit var manager: AutoLoginManager

    private val isLoggedIn get() = testCurrentUserProvider.user != null

    @Before
    fun setup() {
        MockKAnnotations.init(this)

        scope = TestScope()
        configFlow = MutableStateFlow(null)
        testCurrentUserProvider = TestCurrentUserProvider(null)
        foregroundActivityFlow = MutableStateFlow(null)
        foregroundActivityTracker = ForegroundActivityTracker(scope.backgroundScope, foregroundActivityFlow)
        coEvery { notificationHelper.showInformationNotification(any()) } returns Unit
        coEvery { logout.invoke() } answers { testCurrentUserProvider.set(null, null) }

        autoLogin = FakeAutoLogin { managedConfig ->
            delay(500)
            if (managedConfig.username !in setOf("user", "new_user") || managedConfig.password != "pass") {
                Result.failure(BadCredentials)
            } else {
                testCurrentUserProvider.set(null, createAccountUser(UserId("userId"), name = managedConfig.username))
                Result.success(UserId("123"))
            }
        }

        manager = AutoLoginManager(
            scope.backgroundScope,
            ManagedConfig(configFlow),
            { autoLogin },
            { CurrentUser(testCurrentUserProvider) },
            { foregroundActivityTracker },
            { notificationHelper },
            { resetUiForAutoLogin },
            { logout }
        )
    }

    @Test
    fun `auto login disabled with no configuration`() = scope.runTest {
        runCurrent()
        assertEquals(AutoLoginState.Disabled, manager.state.first())
        assertFalse(isLoggedIn)
    }

    @Test
    fun `auto login succeeds with expected credentials`() = scope.runTest {
        configFlow.value = AutoLoginConfig("user", "pass")
        runCurrent()
        assertEquals(AutoLoginState.Ongoing, manager.state.first())
        advanceTimeBy(501)
        assertTrue(isLoggedIn)
        assertEquals(AutoLoginState.Success, manager.state.first())
        verifyOrder {
            notificationHelper.showInformationNotification(R.string.notification_auto_login_start)
            notificationHelper.showInformationNotification(R.string.notification_auto_login_success)
        }
    }

    @Test
    fun `notification is not shown in foreground`() = scope.runTest {
        foregroundActivityFlow.value = mockk(relaxed = true)
        runCurrent()
        configFlow.value = AutoLoginConfig("user", "pass")
        advanceTimeBy(501)
        assertTrue(isLoggedIn)
        assertEquals(AutoLoginState.Success, manager.state.first())
        verify(exactly = 0) { notificationHelper.showInformationNotification(R.string.notification_auto_login_success) }
    }

    @Test
    fun `auto login fails with invalid credentials`() = scope.runTest {
        configFlow.value = AutoLoginConfig("user", "wrong")
        advanceTimeBy(501)
        assertFalse(isLoggedIn)
        assertEquals(AutoLoginState.Error(BadCredentials), manager.state.first())
        verifyOrder {
            notificationHelper.showInformationNotification(R.string.notification_auto_login_start)
            notificationHelper.showInformationNotification(R.string.notification_auto_login_error)
        }
    }

    @Test
    fun `auto login restored after force logout`() = scope.runTest {
        configFlow.value = AutoLoginConfig("user", "pass")
        advanceTimeBy(501)
        assertEquals(AutoLoginState.Success, manager.state.first())

        testCurrentUserProvider.set(null, null)
        runCurrent()
        assertFalse(isLoggedIn)
        assertEquals(AutoLoginState.Ongoing, manager.state.first())

        advanceTimeBy(501)
        assertTrue(isLoggedIn)
        assertEquals(AutoLoginState.Success, manager.state.first())
    }

    @Test
    fun `re-login when username change`() = scope.runTest {
        configFlow.value = AutoLoginConfig("user", "pass")
        advanceTimeBy(501)
        assertEquals(AutoLoginState.Success, manager.state.first())

        configFlow.value = AutoLoginConfig("new_user", "pass")
        runCurrent()
        assertFalse(isLoggedIn)
        assertEquals(AutoLoginState.Ongoing, manager.state.first())

        advanceTimeBy(501)
        assertTrue(isLoggedIn)
        assertEquals(AutoLoginState.Success, manager.state.first())
        assertEquals("new_user", testCurrentUserProvider.user?.name)
    }
}

data object BadCredentials: Exception()

class FakeAutoLogin(val block: suspend (AutoLoginConfig) -> Result<UserId>) : AutoLogin {
    override suspend fun execute(config: AutoLoginConfig) = block(config)
}
