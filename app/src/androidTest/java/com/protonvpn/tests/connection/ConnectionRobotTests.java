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

import com.protonvpn.MockSwitch;
import com.protonvpn.actions.ConnectionRobot;
import com.protonvpn.actions.CountriesRobot;
import com.protonvpn.actions.HomeRobot;
import com.protonvpn.actions.MapRobot;
import com.protonvpn.actions.ProfilesRobot;
import com.protonvpn.actions.ServiceRobot;
import com.protonvpn.android.vpn.VpnStateMonitor;
import com.protonvpn.results.ConnectionResult;
import com.protonvpn.tests.testRules.ProtonHomeActivityTestRule;
import com.protonvpn.tests.testRules.SetUserPreferencesRule;
import com.protonvpn.testsHelper.TestUser;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.rule.ServiceTestRule;

@LargeTest
@RunWith(AndroidJUnit4.class)
public class ConnectionRobotTests {

    private HomeRobot homeRobot = new HomeRobot();
    private ServiceRobot service = new ServiceRobot();

    @Rule public ProtonHomeActivityTestRule testRule = new ProtonHomeActivityTestRule();

    @ClassRule static public final ServiceTestRule SERVICE_RULE = new ServiceTestRule();
    @ClassRule static public SetUserPreferencesRule testClassRule =
        new SetUserPreferencesRule(TestUser.getPlusUser());

    @Test
    public void connectAndDisconnectViaQuickConnect() {
        testRule.mockStatusOnConnect(VpnStateMonitor.State.CONNECTED);
        ConnectionRobot connectionRobot =
            homeRobot.connectThroughQuickConnect().isConnectedToVpn().connectionRobot;
        ConnectionResult result = connectionRobot.clickDisconnectButton();

        result.isDisconnectedFromVpn();
    }

    @Test
    public void connectAndDisconnectViaCountryList() {
        testRule.mockStatusOnConnect(VpnStateMonitor.State.CONNECTED);
        CountriesRobot countriesRobot = homeRobot.clickOnCountriesTab();

        String country = service.getFirstCountryFromBackend();
        countriesRobot.selectCountry(country);
        countriesRobot.selectFastestServer();

        ConnectionResult result = countriesRobot.clickConnectButton();

        ConnectionRobot connectionRobot = result.isConnectedToVpn().connectionRobot;
        connectionRobot.clickDisconnectButton().isDisconnectedFromVpn();

    }

    @Test
    public void connectAndDisconnectViaMapView() {
        testRule.mockStatusOnConnect(VpnStateMonitor.State.CONNECTED);

        MapRobot mapRobot = homeRobot.clickOnMapViewTab();
        mapRobot.clickOnUSNode();
        ConnectionResult result = mapRobot.clickConnectButton();

        ConnectionRobot connectionRobot = result.isConnectedToVpn().connectionRobot;
        connectionRobot.clickDisconnectButton().isDisconnectedFromVpn();
    }

    @Test
    public void connectAndDisconnectViaProfiles() {
        testRule.mockStatusOnConnect(VpnStateMonitor.State.CONNECTED);

        ProfilesRobot profilesRobot = homeRobot.clickOnProfilesTab().isSuccess();
        profilesRobot.clickOnFastestOption();
        ConnectionResult result = profilesRobot.clickOnConnectButton();

        ConnectionRobot connectionRobot = result.isConnectedToVpn().connectionRobot;
        connectionRobot.clickDisconnectButton().isDisconnectedFromVpn();
    }

    @Test
    public void cancelWhileConnecting() {
        testRule.mockStatusOnConnect(VpnStateMonitor.State.CONNECTING);
        homeRobot.connectThroughQuickConnectWithoutVPNHandling();

        ConnectionResult connectionResult = new ConnectionRobot().clickCancelConnectionButton();
        connectionResult.isDisconnectedFromVpn();
    }

    @Test
    public void connectToServerWhenInternetIsDownViaProfiles() {
        testRule.mockErrorOnConnect(VpnStateMonitor.ErrorState.UNREACHABLE);
        testRule.mockStatusOnConnect(VpnStateMonitor.State.ERROR);

        ProfilesRobot profilesRobot = homeRobot.clickOnProfilesTab().isSuccess();
        profilesRobot.clickOnFastestOption();

        ConnectionResult connectionResult = profilesRobot.clickOnConnectButton();

        if (!MockSwitch.mockedConnectionUsed) {
            ServiceRobot serviceRobot = new ServiceRobot();
            serviceRobot.setVpnServiceAsUnreachable();
            serviceRobot.waitUntilServiceIsUnreachable();
        }

        connectionResult.connectionRobot.checkIfNotReachableErrorAppears().clickCancelRetry();
        connectionResult.isDisconnectedFromVpn();
    }
}
