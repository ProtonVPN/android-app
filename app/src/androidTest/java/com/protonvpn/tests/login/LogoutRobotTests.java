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
import com.protonvpn.android.vpn.VpnStateMonitor;
import com.protonvpn.results.LogoutResult;
import com.protonvpn.tests.testRules.ProtonHomeActivityTestRule;
import com.protonvpn.tests.testRules.SetUserPreferencesRule;
import com.protonvpn.testsHelper.TestUser;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;

@LargeTest
@RunWith(AndroidJUnit4.class)
public class LogoutRobotTests {

    private HomeRobot homeRobot = new HomeRobot();

    @Rule public ProtonHomeActivityTestRule testRule = new ProtonHomeActivityTestRule();
    @ClassRule static public SetUserPreferencesRule testClassRule =
        new SetUserPreferencesRule(TestUser.getPlusUser());

    @Test
    public void successfulLogout() {
        LogoutResult result = homeRobot.logout();
        result.isSuccessful();
    }

    @Test
    public void logoutWhileConnectedToVpn() {
        testRule.mockStatusOnConnect(VpnStateMonitor.State.CONNECTED);
        homeRobot.connectThroughQuickConnect().isConnectedToVpn();

        homeRobot.logout().isFailure().warningMessageIsDisplayed();

        LogoutResult result = homeRobot.logoutAfterWarning();

        result.isSuccessful().connectionResult.isDisconnectedFromVpn();
    }

    @Test
    public void cancelLogoutWhileConnectedToVpn() {
        testRule.mockStatusOnConnect(VpnStateMonitor.State.CONNECTED);
        homeRobot.connectThroughQuickConnect().isConnectedToVpn();

        homeRobot.logout().isFailure().warningMessageIsDisplayed();

        LogoutResult result = homeRobot.cancelLogoutAfterWarning();

        result.isFailure().connectionResult.isConnectedToVpn();

        //performs logout after test
        homeRobot.logout().isFailure().warningMessageIsDisplayed().logoutAfterWarning();

        result.isSuccessful().connectionResult.isDisconnectedFromVpn();
    }
}
