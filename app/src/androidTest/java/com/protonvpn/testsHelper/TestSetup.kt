/*
 *  Copyright (c) 2021 Proton AG
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
import com.protonvpn.android.ui.onboarding.OnboardingPreferences
import com.protonvpn.android.utils.Storage
import me.proton.core.test.quark.Quark
import me.proton.core.util.kotlin.deserialize
import me.proton.core.configuration.EnvironmentConfigurationDefaults
import me.proton.core.util.kotlin.EMPTY_STRING

private const val INTERNAL_API_JSON_PATH = "sensitive/internal_apis.json"

object TestSetup {
    val quark: Quark by lazy {
        Quark(
            EnvironmentConfigurationDefaults.apiHost,
            EnvironmentConfigurationDefaults.proxyToken ?: EMPTY_STRING,
            InstrumentationRegistry
                .getInstrumentation()
                .context
                .assets
                .open(INTERNAL_API_JSON_PATH)
                .bufferedReader()
                .use { it.readText() }
                .deserialize())
    }

    fun setCompletedOnboarding() {
        //set flag to slide show to be visible
        Storage.saveBoolean(OnboardingPreferences.FLOATING_BUTTON_USED, true)
    }
}
