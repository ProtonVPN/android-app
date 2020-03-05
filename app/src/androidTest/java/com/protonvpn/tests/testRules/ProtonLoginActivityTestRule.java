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

import com.protonvpn.android.models.config.UserData;
import com.protonvpn.android.ui.login.LoginActivity;

import org.junit.rules.TestWatcher;
import org.junit.runner.Description;

import androidx.test.rule.ActivityTestRule;

public class ProtonLoginActivityTestRule extends TestWatcher {

    public ActivityTestRule<LoginActivity> activityTestRule =
        new ActivityTestRule<>(LoginActivity.class, false, false);

    UserData userData;

    @Override
    protected void starting(Description description) {
        activityTestRule.launchActivity(null);
        userData = activityTestRule.getActivity().userPrefs;
    }

    @Override
    protected void finished(Description description) {
        userData.logout();
    }

    public LoginActivity getActivity() {
        return activityTestRule.getActivity();
    }
}
