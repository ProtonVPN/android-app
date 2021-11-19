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
package com.protonvpn.testRules

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.ActivityTestRule
import com.protonvpn.android.ui.settings.SettingsActivity
import org.junit.rules.TestWatcher
import org.junit.runner.Description

class ProtonSettingsActivityTestRule : TestWatcher() {
    var activityTestRule = ActivityTestRule(
        SettingsActivity::class.java, false, false
    )

    override fun starting(description: Description) {
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("echo \"chrome --disable-fre --no-default-browser-check --no-first-run\" > /data/local/tmp/chrome-command-line")
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("am set-debug-app --persistent com.android.chrome ")
        activityTestRule.launchActivity(null)
    }

    override fun finished(description: Description) {
        activityTestRule.finishActivity()
    }
}