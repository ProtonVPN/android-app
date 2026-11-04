/*
 * Copyright (c) 2026. Proton AG
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
import com.protonvpn.test.shared.TestUser
import com.protonvpn.testsHelper.featureFlagsResponseRule
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.runBlocking
import me.proton.core.featureflag.domain.repository.FeatureFlagRepository
import org.junit.rules.TestWatcher
import org.junit.runner.Description

// Remove together with the TvMainScreenRedesign feature flag.

/** Parameters for tests that need to run with both the legacy and redesigned TV main screen. */
val TV_MAIN_SCREEN_REDESIGN_VARIANTS = listOf(false, true)

/**
 * Sets the TvMainScreenRedesign feature flag via the mock API.
 * Must be placed after [ProtonHiltAndroidRule] and [SetLoggedInUserRule] and before the activity is launched.
 * When [user] is null (user logs in during the test) the flags are fetched by the app after login.
 */
class TvMainScreenRedesignRule(
    private val protonHiltRule: ProtonHiltAndroidRule,
    private val isEnabled: Boolean,
    private val user: TestUser?,
) : TestWatcher() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface TvMainScreenRedesignRuleEntryPoint {
        fun featureFlagRepository(): FeatureFlagRepository
    }

    override fun starting(description: Description) {
        protonHiltRule.mockDispatcher.prependRules {
            featureFlagsResponseRule("TvMainScreenRedesign" to isEnabled)
        }
        if (user != null) {
            val featureFlagRepository = EntryPointAccessors.fromApplication(
                ApplicationProvider.getApplicationContext<Application>(),
                TvMainScreenRedesignRuleEntryPoint::class.java
            ).featureFlagRepository()
            runBlocking { featureFlagRepository.getAll(user.vpnUser.userId) }
        }
    }
}
