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

import com.protonvpn.android.utils.Storage;
import com.protonvpn.testsHelper.TestSetup;
import com.protonvpn.testsHelper.TestUser;
import com.protonvpn.testsHelper.UserDataHelper;

import org.junit.rules.TestWatcher;
import org.junit.runner.Description;

public class SetUserPreferencesRule extends TestWatcher {

    private static UserDataHelper userDataHelper = new UserDataHelper();
    TestUser user;

    public SetUserPreferencesRule(TestUser testUser) {
        user = testUser;
    }

    @Override
    protected void starting(Description description) {
        TestSetup.setCompletedOnboarding();
        if (user != null) {
            userDataHelper.setUserData(user);
        }
    }

    @Override
    protected void finished(Description description) {
        Storage.clearAllPreferences();
    }
}
