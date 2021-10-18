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
package com.protonvpn.testsHelper

import androidx.test.platform.app.InstrumentationRegistry
import com.protonvpn.android.ProtonApplication
import com.protonvpn.android.models.config.UserData
import com.protonvpn.android.models.config.VpnProtocol
import com.protonvpn.android.tv.TvLoginActivity
import com.protonvpn.android.ui.home.LogoutHandler
import com.protonvpn.android.ui.login.LoginActivity
import com.protonvpn.android.utils.AndroidUtils.isTV
import com.protonvpn.test.shared.TestUser
import dagger.hilt.EntryPoint
import dagger.hilt.EntryPoints
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

class UserDataHelper {

    private lateinit var userData: UserData
    private lateinit var logoutHandler: LogoutHandler

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface UserDataHelperEntryPoint {
        fun userData(): UserData
        fun logoutHandler(): LogoutHandler
    }

    init {
        val hiltEntry = EntryPoints.get(
            ProtonApplication.getAppContext(), UserDataHelperEntryPoint::class.java)
        runBlocking(Dispatchers.Main.immediate) {
            userData = hiltEntry.userData()
            logoutHandler = hiltEntry.logoutHandler()
        }
    }

    fun setUserData(user: TestUser) = runBlocking(Dispatchers.Main) {
        userData.isLoggedIn = true
        userData.user = user.email
        userData.vpnInfoResponse = user.vpnInfoResponse
    }

    fun setProtocol(protocol: VpnProtocol) = runBlocking(Dispatchers.Main)  {
        userData.setProtocols(protocol, null)
    }

    fun logoutUser() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        // Logging out starts the login activity, block it, otherwise it may crash when starting
        // after the test has finished and Hilt can no longer provide dependencies.
        val loginActivityClass =
            if (instrumentation.targetContext.isTV()) TvLoginActivity::class.java
            else LoginActivity::class.java
        val monitor =
            instrumentation.addMonitor(loginActivityClass.canonicalName, null, true)
        runBlocking(Dispatchers.Main) {
            logoutHandler.logout(true)
        }
        // Remove the monitor so that it doesn't avoid any other tests.
        if (!instrumentation.checkMonitorHit(monitor, 1)) {
            monitor.waitForActivityWithTimeout(1000)
            instrumentation.removeMonitor(monitor)
        }
    }
}
