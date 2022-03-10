/*
 * Copyright (c) 2021. Proton AG
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

package com.protonvpn.testRules

import android.app.Activity
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.espresso.IdlingRegistry
import androidx.test.rule.ActivityTestRule
import com.protonvpn.android.vpn.ErrorType
import com.protonvpn.android.vpn.VpnState
import com.protonvpn.testsHelper.ServiceTestHelper
import com.protonvpn.testsHelper.UserDataHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.runner.Description

/**
 * A rule for testing activities in the logged in state.
 */
open class LoggedInActivityTestRule<T : Activity>(activityClass: Class<T>) : InstantTaskExecutorRule() {

    private lateinit var service: ServiceTestHelper
    private lateinit var userDataHelper: UserDataHelper

    private val activityTestRule = ActivityTestRule(activityClass, false, false)

    override fun starting(description: Description) {
        super.starting(description)
        service = ServiceTestHelper()
        userDataHelper = UserDataHelper()
        activityTestRule.launchActivity(null)
    }

    override fun finished(description: Description) {
        super.finished(description)
        runBlocking(Dispatchers.Main) {
            service.enableSecureCore(false)
            service.connectionManager.disconnect("test tear down")
            service.deleteCreatedProfiles()
        }
        activityTestRule.finishActivity()
        IdlingRegistry.getInstance().unregister()
    }

    fun mockStatusOnConnect(state: VpnState) {
        service.mockVpnBackend.stateOnConnect = state
    }

    fun mockErrorOnConnect(type: ErrorType) {
        service.mockVpnBackend.stateOnConnect = VpnState.Error(type, null)
    }
}
