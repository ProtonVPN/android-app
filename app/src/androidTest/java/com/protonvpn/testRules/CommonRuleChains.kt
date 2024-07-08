/*
 *
 *  * Copyright (c) 2023. Proton AG
 *  *
 *  * This file is part of ProtonVPN.
 *  *
 *  * ProtonVPN is free software: you can redistribute it and/or modify
 *  * it under the terms of the GNU General Public License as published by
 *  * the Free Software Foundation, either version 3 of the License, or
 *  * (at your option) any later version.
 *  *
 *  * ProtonVPN is distributed in the hope that it will be useful,
 *  * but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  * GNU General Public License for more details.
 *  *
 *  * You should have received a copy of the GNU General Public License
 *  * along with ProtonVPN.  If not, see <https://www.gnu.org/licenses/>.
 *
 */

package com.protonvpn.testRules

import android.content.ComponentName
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.protonvpn.android.redesign.app.ui.MainActivity
import com.protonvpn.mocks.TestApiConfig
import com.protonvpn.test.shared.TestUser
import me.proton.test.fusion.FusionConfig
import org.junit.rules.RuleChain

object CommonRuleChains {

    fun Any.realBackendRule(): RuleChain {
        enableNonAliasActivityForTests()
        val hiltRule = ProtonHiltAndroidRule(this, TestApiConfig.Backend)

        return RuleChain
            .outerRule(TestSettingsOverrideRule(false))
            .around(hiltRule)
            .around(ProtonHiltInjectRule(hiltRule))
    }

    fun Any.realBackendComposeRule(): RuleChain {
        enableNonAliasActivityForTests()
        val composeRule = createAndroidComposeRule<MainActivity>()
        FusionConfig.Compose.testRule.set(composeRule)

        return realBackendRule()
            .around(composeRule)
    }

    fun Any.mockedLoggedInRule(
        testUser: TestUser = TestUser.plusUser,
        mockedBackend: TestApiConfig = TestApiConfig.Mocked(testUser),
        // Not using generics to allow for default value.
        activityClass: Class<out ComponentActivity> = MainActivity::class.java
    ): RuleChain {
        enableNonAliasActivityForTests(activityClass)
        val hiltRule = ProtonHiltAndroidRule(this, mockedBackend)
        val composeRule = createAndroidComposeRule(activityClass)
        FusionConfig.Compose.testRule.set(composeRule)

        return RuleChain
            .outerRule(hiltRule)
            .around(SetLoggedInUserRule(testUser))
            .around(ProtonHiltInjectRule(hiltRule))
            .around(composeRule)
    }

    private fun enableNonAliasActivityForTests(activityClass: Class<out ComponentActivity> = MainActivity::class.java) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.packageManager.setComponentEnabledSetting(ComponentName(context, activityClass), PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)

    }
}
