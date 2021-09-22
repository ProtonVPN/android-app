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
package com.protonvpn.tests.connection;

import com.protonvpn.actions.ConnectionRobot;
import com.protonvpn.actions.CountriesRobot;
import com.protonvpn.actions.HomeRobot;
import com.protonvpn.actions.MapRobot;
import com.protonvpn.actions.ProfilesRobot;
import com.protonvpn.actions.ServiceRobot;
import com.protonvpn.android.vpn.ErrorType;
import com.protonvpn.android.vpn.VpnState;
import com.protonvpn.results.ConnectionResult;
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
import androidx.test.filters.SdkSuppress;
import dagger.hilt.android.testing.HiltAndroidRule;
import dagger.hilt.android.testing.HiltAndroidTest;

@LargeTest
@RunWith(AndroidJUnit4.class)
@HiltAndroidTest
public class ConnectionRobotTests {

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
    public void connectAndDisconnectViaQuickConnect() {
        testRule.mockStatusOnConnect(VpnState.Connected.INSTANCE);
        ConnectionRobot connectionRobot =
            homeRobot.connectThroughQuickConnect().isConnectedToVpn().connectionRobot;
        ConnectionResult result = connectionRobot.clickDisconnectButton();

        result.isDisconnectedFromVpn();
    }

    @Test
    public void connectAndDisconnectViaCountryList() {
        testRule.mockStatusOnConnect(VpnState.Connected.INSTANCE);
        CountriesRobot countriesRobot = homeRobot.clickOnCountriesTab();

        String country = new ServiceRobot().getFirstCountryFromBackend();
        countriesRobot.selectCountry(country);

        ConnectionResult result = countriesRobot.connectToFastestServer();

        ConnectionRobot connectionRobot = result.isConnectedToVpn().connectionRobot;
        connectionRobot.clickDisconnectButton().isDisconnectedFromVpn();
    }

    @Test
    public void connectAndDisconnectViaMapView() {
        testRule.mockStatusOnConnect(VpnState.Connected.INSTANCE);

        MapRobot mapRobot = homeRobot.clickOnMapViewTab();
        mapRobot.clickOnUSNode();
        ConnectionResult result = mapRobot.clickConnectButton();

        ConnectionRobot connectionRobot = result.isConnectedToVpn().connectionRobot;
        connectionRobot.clickDisconnectButton().isDisconnectedFromVpn();
    }

    @Test
    public void connectAndDisconnectViaProfiles() {
        testRule.mockStatusOnConnect(VpnState.Connected.INSTANCE);

        ProfilesRobot profilesRobot = homeRobot.clickOnProfilesTab().isSuccess();
        ConnectionResult result = profilesRobot.clickOnConnectButton("Fastest");

        ConnectionRobot connectionRobot = result.isConnectedToVpn().connectionRobot;
        connectionRobot.clickDisconnectButton().isDisconnectedFromVpn();
    }

    @SdkSuppress(minSdkVersion = 24)
    @Test
    public void cancelWhileConnecting() {
        testRule.mockStatusOnConnect(VpnState.Connecting.INSTANCE);
        homeRobot.connectThroughQuickConnectWithoutVPNHandling();

        ConnectionResult connectionResult = new ConnectionRobot().clickCancelConnectionButton();
        connectionResult.isDisconnectedFromVpn();
    }

    @Test
    public void connectToServerWhenInternetIsDownViaProfiles() {
        testRule.mockErrorOnConnect(ErrorType.UNREACHABLE);

        ProfilesRobot profilesRobot = homeRobot.clickOnProfilesTab().isSuccess();

        ConnectionResult connectionResult = profilesRobot.clickOnConnectButton("Fastest");
        connectionResult.connectionRobot.checkIfNotReachableErrorAppears().clickCancelRetry();
        connectionResult.isDisconnectedFromVpn();
    }
}
