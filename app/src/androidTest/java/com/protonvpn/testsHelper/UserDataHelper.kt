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

import com.protonvpn.android.ProtonApplication
import com.protonvpn.android.models.config.UserData
import com.protonvpn.test.shared.TestUser
import dagger.hilt.EntryPoint
import dagger.hilt.EntryPoints
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

class UserDataHelper {

    @JvmField val userData: UserData

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface UserDataHelperEntryPoint {
        fun userData(): UserData
    }

    init {
        val hiltEntry = EntryPoints.get(
            ProtonApplication.getAppContext(), UserDataHelperEntryPoint::class.java)
        userData = hiltEntry.userData()
    }

    fun setUserData(user: TestUser) {
        userData.isLoggedIn = true
        userData.user = user.email
        userData.vpnInfoResponse = user.vpnInfoResponse
    }

    fun logoutUser() {
        userData.isLoggedIn = false
    }
}
