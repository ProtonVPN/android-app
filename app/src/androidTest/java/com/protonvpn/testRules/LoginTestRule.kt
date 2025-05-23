/*
 * Copyright (c) 2022. Proton AG
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

import android.app.Application
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import com.protonvpn.android.utils.Storage
import com.protonvpn.test.shared.TestUser
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import me.proton.core.auth.presentation.testing.ProtonTestEntryPoint
import me.proton.core.test.quark.Quark
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import kotlin.system.measureTimeMillis

class LoginTestRule(
    private val username: String,
    private val password: String
) : TestWatcher() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Dependencies : ProtonTestEntryPoint {
        val quark: Quark
    }

    constructor(testUser: TestUser) : this(testUser.email, testUser.password)

    override fun starting(description: Description?) {
        super.starting(description)
        val dependencies: Dependencies = EntryPointAccessors.fromApplication(
            ApplicationProvider.getApplicationContext<Application>(),
            Dependencies::class.java
        )
        val loginTimeMs = measureTimeMillis {
            dependencies.quark.jailUnban()
            dependencies.loginTestHelper.login(username, password)
        }
        Log.d("LoginTestRule", "login took ${loginTimeMs}ms")
    }

    override fun finished(description: Description) {
        Storage.clearAllPreferencesSync()
    }
}
