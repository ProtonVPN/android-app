/*
 * Copyright (c) 2025 Proton AG
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
import androidx.test.core.app.ApplicationProvider
import com.protonvpn.android.appconfig.AppConfig
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.utils.Storage
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.runBlocking
import me.proton.core.auth.presentation.testing.ProtonTestEntryPoint
import org.junit.rules.TestWatcher
import org.junit.runner.Description

class AppConfigRefreshTestRule : TestWatcher() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Dependencies : ProtonTestEntryPoint {

        val appConfig: AppConfig

        val currentUser: CurrentUser

    }

    override fun starting(description: Description?) {
        super.starting(description)

        val dependencies: Dependencies = EntryPointAccessors.fromApplication(
            ApplicationProvider.getApplicationContext<Application>(),
            Dependencies::class.java,
        )

        runBlocking {
            val userId = dependencies.currentUser.user()?.userId

            dependencies.appConfig.forceUpdate(userId = userId)
        }
    }

    override fun finished(description: Description) {
        Storage.clearAllPreferencesSync()
    }

}
