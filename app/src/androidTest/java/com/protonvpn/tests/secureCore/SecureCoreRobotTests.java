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
package com.protonvpn.tests.secureCore;

import com.protonvpn.MockSwitch;
import com.protonvpn.actions.CountriesRobot;
import com.protonvpn.actions.HomeRobot;
import com.protonvpn.actions.MapRobot;
import com.protonvpn.android.vpn.VpnStateMonitor;
import com.protonvpn.results.ConnectionResult;
import com.protonvpn.results.MapResult;
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
public class SecureCoreRobotTests {

    private HomeRobot homeRobot = new HomeRobot();

    @ClassRule static public SetUserPreferencesRule testClassRule =
        new SetUserPreferencesRule(TestUser.getPlusUser());
    @Rule public ProtonHomeActivityTestRule testRule = new ProtonHomeActivityTestRule();

    @Test
    public void connectAndDisconnectFromSecureCoreThroughMap() {
        testRule.mockStatusOnConnect(VpnStateMonitor.State.CONNECTED);

        MapRobot map = homeRobot.clickOnMapViewTab();

        map.enableSecureCore();
        map.clickOnSecureCoreSwedenNode();

        MapResult mapResult = new MapResult();
        mapResult.isSwedenNodeSelected();

        map.clickOnSecureCoreFranceNode();

        ConnectionResult result = map.clickConnectButton();
        result.isConnectedToVpn();

        result.connectionRobot.clickDisconnectButton();
        result.isDisconnectedFromVpn();
    }

    @Test
    public void connectAndDisconnectFromSecureCoreThroughQuickConnect() {
        testRule.mockStatusOnConnect(VpnStateMonitor.State.CONNECTED);

        homeRobot.enableSecureCore();
        ConnectionResult result = homeRobot.connectThroughQuickConnect();
        result.isConnectedToVpn();

        result.connectionRobot.clickDisconnectButton();
        result.isDisconnectedFromVpn();
    }

    @Test
    public void connectAndDisconnectFromSecureCoreThroughCountryList() {
        testRule.mockStatusOnConnect(VpnStateMonitor.State.CONNECTED);

        homeRobot.enableSecureCore();
        CountriesRobot countries = homeRobot.clickOnCountriesTab();
        if (MockSwitch.mockedServersUsed) {
            countries.selectCountry("Finland");
        }
        else {
            countries.selectCountry("Australia");
        }
        countries.selectCountry("via Sweden");

        ConnectionResult result = countries.clickConnectButton();
        result.isConnectedToVpn();

        result.connectionRobot.clickDisconnectButton();
        result.isDisconnectedFromVpn();
    }
}
