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
package com.protonvpn.tests.testRules;

import com.protonvpn.android.ui.settings.SettingsActivity;

import org.junit.rules.TestWatcher;
import org.junit.runner.Description;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;

public class ProtonSettingsActivityTestRule extends TestWatcher {

    public ActivityTestRule<SettingsActivity> activityTestRule =
        new ActivityTestRule<>(SettingsActivity.class, false, false);

    @Override
    protected void starting(Description description) {
        InstrumentationRegistry.getInstrumentation().getUiAutomation().executeShellCommand("echo \"chrome --disable-fre --no-default-browser-check --no-first-run\" > /data/local/tmp/chrome-command-line");
        InstrumentationRegistry.getInstrumentation().getUiAutomation().executeShellCommand("am set-debug-app --persistent com.android.chrome ");
        activityTestRule.launchActivity(null);
    }

    @Override
    protected void finished(Description description) {
        activityTestRule.finishActivity();
    }
}
