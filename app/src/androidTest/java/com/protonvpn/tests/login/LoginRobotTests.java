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
package com.protonvpn.tests.login;

import com.protonvpn.actions.HomeRobot;
import com.protonvpn.actions.LoginRobot;
import com.protonvpn.di.MockNetworkManager;
import com.protonvpn.results.LoginFormResult;
import com.protonvpn.results.LoginResult;
import com.protonvpn.results.LogoutResult;
import com.protonvpn.test.shared.TestUser;
import com.protonvpn.tests.testRules.ProtonLoginActivityTestRule;
import com.protonvpn.tests.testRules.SetUserPreferencesRule;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import dagger.hilt.android.testing.HiltAndroidRule;
import dagger.hilt.android.testing.HiltAndroidTest;
import me.proton.core.network.domain.NetworkStatus;

@LargeTest
@RunWith(AndroidJUnit4.class)
@HiltAndroidTest
public class LoginRobotTests {

    public ProtonLoginActivityTestRule testRule = new ProtonLoginActivityTestRule();
    @Rule public RuleChain rules = RuleChain
        .outerRule(new HiltAndroidRule(this))
        .around(new SetUserPreferencesRule(null))
        .around(testRule);

    private LoginRobot loginRobot;
    private HomeRobot homeRobot;

    @Before
    public void setup() {
        MockNetworkManager.Companion.setCurrentStatus(NetworkStatus.Unmetered);
        loginRobot = new LoginRobot();
        homeRobot = new HomeRobot();
    }

    @Test
    public void loginWithPlusUser() {
        LoginResult result = loginRobot.login(TestUser.getPlusUser());
        result.isSuccessful().usernameDisplayed();
    }

    @Ignore("when API is changed to reset counter of bad login attempts, remove Ignore annotation")
    @Test
    public void loginWithIncorrectCredentials() {
        LoginResult result = loginRobot.login(TestUser.getBadUser());

        result.isFailure().badCredentialsError();
    }

    @Test
    public void checkUserPassword() {
        LoginFormResult result = loginRobot.viewUserPassword(TestUser.getPlusUser());

        result.passwordIsVisible();
    }

    @Ignore("VPNAND-605")
    @Test
    public void needHelpMenuOpened() {
        LoginFormResult result = loginRobot.clickOnNeedHelpButton();

        result.helpMenuIsVisible();
    }

    @Test
    public void loginWhenInternetIsDown() {
        MockNetworkManager.Companion.setCurrentStatus(NetworkStatus.Disconnected);
        LoginResult result = loginRobot.login(TestUser.getPlusUser());
        result.isFailure().noInternetConnectionError(testRule.getActivity());
    }

    @Test
    public void rememberMeFunctionality() {
        TestUser user = loginRobot.login(TestUser.getPlusUser()).isSuccessful().getUser();

        homeRobot.logout().isSuccessful();

        LogoutResult result = new LogoutResult(user);
        result.userNameVisible();
    }
}
