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
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.protonvpn.android.ProtonApplication
import com.protonvpn.mocks.MockInterceptorWrapper
import com.protonvpn.mocks.TestApiConfig
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.components.SingletonComponent
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * Hilt injection in tests for ProtonApplication.
 *
 * It calls ProtonApplication.initDependencies() after Hilt components are initialized but before the
 * test is started.
 *
 * Use it instead of HiltAndroidRule.
 */
class ProtonHiltAndroidRule(
    testInstance: Any,
    private val apiConfig: TestApiConfig
) : TestRule {

    private val hiltAndroidRule = HiltAndroidRule(testInstance)

    @dagger.hilt.EntryPoint
    @InstallIn(SingletonComponent::class)
    interface MockInterceptorEntryPoint {
        fun mockInterceptor(): MockInterceptorWrapper
    }

    override fun apply(base: Statement, description: Description): Statement {
        val statement = object : Statement() {
            override fun evaluate() {
                // Objects created in initDependencies may perform API calls, so setup the mock API rules earlier.
                if (apiConfig is TestApiConfig.Mocked) {
                    val entryPoint: MockInterceptorEntryPoint = EntryPointAccessors.fromApplication(
                        ApplicationProvider.getApplicationContext<Application>(),
                        MockInterceptorEntryPoint::class.java
                    )
                    apiConfig.addDefaultRules(entryPoint.mockInterceptor())
                }

                InstrumentationRegistry.getInstrumentation().runOnMainSync {
                    ApplicationProvider.getApplicationContext<ProtonApplication>().initDependencies()
                }
                base.evaluate()
            }
        }
        return hiltAndroidRule.apply(statement, description)
    }

    fun inject() {
        hiltAndroidRule.inject()
    }
}
