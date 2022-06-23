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

import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.IdlingRegistry
import com.protonvpn.android.ProtonApplication
import com.protonvpn.testsHelper.EspressoDispatcherProvider
import dagger.hilt.EntryPoint
import dagger.hilt.EntryPoints
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import me.proton.core.util.kotlin.DispatcherProvider
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * A rule that registers EspressoDistpatcherProvider as an IdlingResource.
 * It requires that the DispatcherProvider provided by Hilt is an EspressoDispatcherProvider.
 */
class EspressoDispatcherRule : TestRule {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface EspressoDispatcherRuleEntryPoint {
        fun dispatcherProvider(): DispatcherProvider
    }

    override fun apply(base: Statement, description: Description): Statement {
        return object : Statement() {
            override fun evaluate() {
                val hiltEntry = EntryPoints.get(
                    ApplicationProvider.getApplicationContext<ProtonApplication>(),
                    EspressoDispatcherRuleEntryPoint::class.java
                )
                val resource = (hiltEntry.dispatcherProvider() as EspressoDispatcherProvider).idlingResource
                val registry = IdlingRegistry.getInstance()
                registry.register(resource)
                base.evaluate()
                registry.unregister(resource)
            }
        }
    }
}
