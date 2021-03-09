/*
 * Copyright (c) 2018 Proton Technologies AG
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
package com.protonvpn.tests.account;

import com.protonvpn.results.AccountResults;
import com.protonvpn.results.LoginResult;
import com.protonvpn.tests.testRules.ProtonHomeActivityTestRule;
import com.protonvpn.tests.testRules.SetUserPreferencesRule;
import com.protonvpn.test.shared.TestUser;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.rule.ServiceTestRule;

@LargeTest
@RunWith(AndroidJUnit4.class)
public class AccountRobotTests {

    @Rule public ProtonHomeActivityTestRule testRule = new ProtonHomeActivityTestRule();

    @ClassRule static public SetUserPreferencesRule testClassRule =
        new SetUserPreferencesRule(TestUser.getPlusUser());
    @ClassRule static public final ServiceTestRule SERVICE_RULE = new ServiceTestRule();

    @Test
    public void checkIfUserNameDisplayedInAccountSection() {
        LoginResult loginResult = new LoginResult(TestUser.getPlusUser());
        AccountResults success = loginResult.isSuccessful().usernameDisplayed().isSuccess();
        success.manageAccountsButtonHasLink();
    }
}

