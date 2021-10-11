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

import com.protonvpn.test.shared.TestUser
import com.protonvpn.TestApplication
import com.protonvpn.android.models.config.UserData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

class UserDataHelper {

    @Inject lateinit var userData: UserData

    fun setUserData(user: TestUser) = runBlocking(Dispatchers.Main) {
        userData.isLoggedIn = true
        userData.user = user.email
        userData.vpnInfoResponse = user.vpnInfoResponse
    }

    fun logoutUser() {
        userData.isLoggedIn = false
    }

    init {
        TestApplication.testAppComponent.provideUserPrefs(this)
    }
}
