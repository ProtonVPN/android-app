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
import com.protonvpn.actions.ProfilesRobot;
import com.protonvpn.android.models.config.VpnProtocol;
import com.protonvpn.android.models.profiles.Profile;
import com.protonvpn.results.ConnectionResult;
import com.protonvpn.results.HomeResult;
import com.protonvpn.tests.testRules.ProtonHomeActivityTestRule;
import com.protonvpn.tests.testRules.SetUserPreferencesRule;
import com.protonvpn.test.shared.TestUser;
import com.protonvpn.testsHelper.ServiceTestHelper;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;

@LargeTest
@RunWith(AndroidJUnit4.class)
public class SecureCoreRobotSecurityTests {

    private HomeRobot homeRobot = new HomeRobot();

    @ClassRule static public SetUserPreferencesRule testClassRule =
        new SetUserPreferencesRule(TestUser.getFreeUser());
    @Rule public ProtonHomeActivityTestRule testRule = new ProtonHomeActivityTestRule();

    @Test
    public void tryToEnableSecureCoreAsFreeUser() {
        HomeResult result = homeRobot.enableSecureCore();
        result
            .dialogUpgradeVisible()
            .checkSecureCoreDisabled();
    }

    @Test
    public void tryToConnectToSecureCoreThroughProfilesAsFreeUser() {
        String TEST_PROFILE = "Test profile";
        ServiceTestHelper.addProfile(VpnProtocol.Smart, TEST_PROFILE, "se-fr-01.protonvpn.com");

        ProfilesRobot profilesRobot = homeRobot.clickOnProfilesTab().isSuccess();
        ConnectionResult result = profilesRobot.clickOnUpgradeButton(TEST_PROFILE);
        result.cannotAccessSecureCoreAsFreeUser();
        result.isDisconnectedFromVpn();
    }

    @Test
    public void tryToConnectToSecureCoreThroughQuickConnectAsFreeUser() {
        String TEST_PROFILE = "Test profile";
        Profile testProfile =
            ServiceTestHelper.addProfile(VpnProtocol.Smart, TEST_PROFILE, "se-fr-01.protonvpn.com");
        ServiceTestHelper.setDefaultProfile(testProfile);

        ConnectionResult result = homeRobot.connectThroughQuickConnect(TEST_PROFILE);
        result.cannotAccessSecureCoreAsFreeUser();
        result.isDisconnectedFromVpn();
    }
}
