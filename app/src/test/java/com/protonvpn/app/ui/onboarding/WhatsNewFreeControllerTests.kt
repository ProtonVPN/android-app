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
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.ui.onboarding.WhatsNewDialogController
import com.protonvpn.android.ui.onboarding.WhatsNewDialogType
import com.protonvpn.test.shared.MockSharedPreferencesProvider
import com.protonvpn.test.shared.TestCurrentUserProvider
import com.protonvpn.test.shared.TestUser
import com.protonvpn.test.shared.runWhileCollecting
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WhatsNewFreeControllerTests {

    private lateinit var currentUser: CurrentUser
    private lateinit var prefs: AppFeaturesPrefs
    private lateinit var testScope: TestScope
    private lateinit var testUserProvider: TestCurrentUserProvider

    @Before
    fun setup() {
        testScope = TestScope(UnconfinedTestDispatcher())

        testUserProvider = TestCurrentUserProvider(TestUser.freeUser.vpnUser)
        currentUser = CurrentUser(testUserProvider)
        prefs = AppFeaturesPrefs(MockSharedPreferencesProvider())
        prefs.showWhatsNew = true
    }

    @Test
    fun `shouldShowDialog emits null after dialog is shown`() = testScope.runTest {
        val controller = createController()

        val shouldShowDialogValues = runWhileCollecting(controller.shouldShowDialog()) {
            controller.onDialogShown()
        }
        assertEquals(listOf(WhatsNewDialogType.Free, null), shouldShowDialogValues)
    }

    @Test
    fun `shouldShowDialog emits true only after user is logged in`() = testScope.runTest {
        val controller = createController()
        testUserProvider.vpnUser = null
        val shouldShowDialogValues = runWhileCollecting(controller.shouldShowDialog()) {
            testUserProvider.vpnUser = TestUser.plusUser.vpnUser
        }

        assertEquals(listOf(null, WhatsNewDialogType.Paid), shouldShowDialogValues)
    }

    private fun createController() = WhatsNewDialogController(currentUser, prefs)
}
