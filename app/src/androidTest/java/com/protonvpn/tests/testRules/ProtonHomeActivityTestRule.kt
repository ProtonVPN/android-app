/*
 * Copyright (c) 2019 Proton Technologies AG
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
package com.protonvpn.tests.testRules

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.rule.ActivityTestRule
import androidx.test.rule.ServiceTestRule
import com.protonvpn.android.ui.home.HomeActivity
import com.protonvpn.android.vpn.ErrorType
import com.protonvpn.android.vpn.VpnState
import com.protonvpn.testsHelper.ServiceTestHelper
import com.protonvpn.testsHelper.UserDataHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.runner.Description

class ProtonHomeActivityTestRule : InstantTaskExecutorRule() {
    private var service: ServiceTestHelper? = null
    private val userDataHelper = UserDataHelper()
    var activityTestRule: ActivityTestRule<HomeActivity> =
        ActivityTestRule<HomeActivity>(HomeActivity::class.java, false, false)

    override fun starting(description: Description) {
        super.starting(description)
        if (service == null) {
            service = ServiceTestHelper(ServiceTestRule())
        }
        val testIntent = TestIntent()
        activityTestRule.launchActivity(testIntent.intent)
    }

    override fun finished(description: Description) {
        super.finished(description)
        runBlocking(Dispatchers.Main) {
            service!!.enableSecureCore(false)
            ServiceTestHelper.connectionManager.disconnect()
            service!!.disconnectFromServer()
            userDataHelper.userData.logout()
            ServiceTestHelper.deleteCreatedProfiles()
        }
        activityTestRule.finishActivity()
    }

    fun mockStatusOnConnect(state: VpnState) {
        service!!.mockVpnBackend.stateOnConnect = state
    }

    fun mockErrorOnConnect(type: ErrorType) {
        service!!.mockVpnBackend.stateOnConnect = VpnState.Error(type, null)
    }

    val activity: HomeActivity
        get() = activityTestRule.getActivity()
}