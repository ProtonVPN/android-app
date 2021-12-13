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
import com.protonvpn.android.vpn.VpnState;
import com.protonvpn.results.LogoutResult;
import com.protonvpn.test.shared.TestUser;
import com.protonvpn.tests.testRules.ProtonHomeActivityTestRule;
import com.protonvpn.tests.testRules.SetUserPreferencesRule;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import dagger.hilt.android.testing.HiltAndroidRule;
import dagger.hilt.android.testing.HiltAndroidTest;

@LargeTest
@RunWith(AndroidJUnit4.class)
@HiltAndroidTest
public class LogoutRobotTests {

    private final ProtonHomeActivityTestRule testRule = new ProtonHomeActivityTestRule();
    @Rule public RuleChain rules = RuleChain
        .outerRule(new HiltAndroidRule(this))
        .around(new SetUserPreferencesRule(TestUser.getPlusUser()))
        .around(testRule);

    private HomeRobot homeRobot;

    @Before
    public void setup() {
        homeRobot = new HomeRobot();
    }

    @Test
    public void successfulLogout() {
        LogoutResult result = homeRobot.logout();
        result.isSuccessful();
    }

    @Test
    public void logoutWhileConnectedToVpn() {
        testRule.mockStatusOnConnect(VpnState.Connected.INSTANCE);
        homeRobot.connectThroughQuickConnect().isConnectedToVpn();

        homeRobot.logout().noLoginScreen().warningMessageIsDisplayed();

        LogoutResult result = homeRobot.logoutAfterWarning();

        result.isSuccessful().connectionResult.isDisconnectedFromVpn();
    }

    @Test
    public void cancelLogoutWhileConnectedToVpn() {
        testRule.mockStatusOnConnect(VpnState.Connected.INSTANCE);
        homeRobot.connectThroughQuickConnect().isConnectedToVpn();

        homeRobot.logout().noLoginScreen().warningMessageIsDisplayed();

        LogoutResult result = homeRobot.cancelLogoutAfterWarning();

        result.noLoginScreen().connectionResult.isConnectedToVpn();

        //performs logout after test
        homeRobot.logout().warningMessageIsDisplayed().logoutAfterWarning();

        result.isSuccessful().connectionResult.isDisconnectedFromVpn();
    }
}
