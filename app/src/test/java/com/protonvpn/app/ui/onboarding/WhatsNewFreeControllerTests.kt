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

import com.protonvpn.android.appconfig.AppFeaturesPrefs
import com.protonvpn.android.appconfig.ChangeServerConfig
import com.protonvpn.android.appconfig.Restrictions
import com.protonvpn.android.appconfig.RestrictionsConfig
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.auth.usecase.CurrentUserProvider
import com.protonvpn.android.ui.onboarding.WhatsNewFreeController
import com.protonvpn.test.shared.MockSharedPreferencesProvider
import com.protonvpn.test.shared.TestCurrentUserProvider
import com.protonvpn.test.shared.TestUser
import com.protonvpn.test.shared.runWhileCollecting
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WhatsNewFreeControllerTests {

    private lateinit var currentUser: CurrentUser
    private lateinit var restrictionsConfig: RestrictionsConfig
    private lateinit var restrictionsFlow: MutableStateFlow<Restrictions>
    private lateinit var prefs: AppFeaturesPrefs
    private lateinit var testScope: TestScope
    private lateinit var testUserProvider: TestCurrentUserProvider

    private val changeServerConfig = ChangeServerConfig(10, 5, 60)

    @Before
    fun setup() {
        testScope = TestScope(UnconfinedTestDispatcher())

        testUserProvider = TestCurrentUserProvider(TestUser.freeUser.vpnUser)
        currentUser = CurrentUser(testScope.backgroundScope, testUserProvider)
        restrictionsFlow = MutableStateFlow(Restrictions(false, changeServerConfig))
        restrictionsConfig = RestrictionsConfig(testScope.backgroundScope, restrictionsFlow)
        prefs = AppFeaturesPrefs(MockSharedPreferencesProvider())
    }

    @Test
    fun `shouldShowDialog emits false after dialog is shown`() = testScope.runTest {
        restrictionsFlow.value = Restrictions(true, changeServerConfig)
        val controller = createController()

        val shouldShowDialogValues = runWhileCollecting(controller.shouldShowDialog()) {
            controller.onDialogShown()
        }
        assertEquals(listOf(true, false), shouldShowDialogValues)
    }

    @Test
    fun `logging in with restrictions enabled disables the dialog`() = testScope.runTest {
        restrictionsFlow.value = Restrictions(true, changeServerConfig)
        val controller = createController()
        controller.initOnLogin()
        assertFalse(controller.shouldShowDialog().first())
    }

    @Test
    fun `logging in with restrictions disabled doesn't disable the dialog`() = testScope.runTest {
        restrictionsFlow.value = Restrictions(false, changeServerConfig)
        val controller = createController()
        controller.initOnLogin()
        restrictionsFlow.value = Restrictions(true, changeServerConfig)
        assertTrue(controller.shouldShowDialog().first())
    }

    @Test
    fun `user downgraded to restricted UI doesn't see the dialog`() = testScope.runTest {
        val controller = createController()
        testUserProvider.vpnUser = TestUser.plusUser.vpnUser
        restrictionsFlow.value = Restrictions(true, changeServerConfig)

        testUserProvider.vpnUser = TestUser.freeUser.vpnUser
        restrictionsFlow.value = Restrictions(true, changeServerConfig)
        assertFalse(controller.shouldShowDialog().first())
    }

    @Test
    fun `downgraded user sees the dialog when restrictions are enabled`() = testScope.runTest {
        val controller = createController()
        testUserProvider.vpnUser = TestUser.plusUser.vpnUser
        testUserProvider.vpnUser = TestUser.freeUser.vpnUser

        restrictionsFlow.value = Restrictions(true, changeServerConfig)
        assertTrue(controller.shouldShowDialog().first())
    }

    private fun TestScope.createController() =
        WhatsNewFreeController(backgroundScope, prefs, restrictionsConfig, currentUser)
}
