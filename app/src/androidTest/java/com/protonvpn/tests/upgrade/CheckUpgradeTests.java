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
package com.protonvpn.tests.upgrade;

import com.protonvpn.actions.HomeRobot;
import com.protonvpn.actions.LoginRobot;
import com.protonvpn.actions.SettingsRobot;
import com.protonvpn.results.ProfilesResult;
import com.protonvpn.results.SettingsResults;
import com.protonvpn.test.shared.TestUser;
import com.protonvpn.tests.testRules.ProtonLoginActivityTestRule;
import com.protonvpn.tests.testRules.SetUserPreferencesForUpgradeRule;

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

@Ignore
@LargeTest
@RunWith(AndroidJUnit4.class)
@HiltAndroidTest
public class CheckUpgradeTests {

    private final ProtonLoginActivityTestRule testRule = new ProtonLoginActivityTestRule();
    @Rule public RuleChain rules = RuleChain
        .outerRule(new HiltAndroidRule(this))
        .around(new SetUserPreferencesForUpgradeRule(null))
        .around(testRule);

    private HomeRobot homeRobot;
    private SettingsResults results;
    private ProfilesResult profilesResults;
    private SettingsRobot settingsRobot;
    private LoginRobot loginRobot;

    @Before
    public void setup() {
        homeRobot = new HomeRobot();
        results = new SettingsResults();
        profilesResults = new ProfilesResult();
        settingsRobot = new SettingsRobot();
        loginRobot = new LoginRobot();
    }

    @Test
    public void checkSettings() {
        loginRobot.login(TestUser.getPlusUser());
        homeRobot.openSettings();

        results.checkIfRandomServerIsSelected();
        results.checkIfMTUIsSet();

        settingsRobot.toggleSplitTunneling();

        settingsRobot.openExcludedIPAddressesList();
        results.checkIfIPAddressIsExcluded();

        settingsRobot.clickOnDoneButton();
        settingsRobot.toggleSplitTunneling();

        settingsRobot.navigateBackToHomeScreen();
    }

    @Test
    public void checkProfile() {
        LoginRobot loginRobot = new LoginRobot();
        loginRobot.login(TestUser.getPlusUser());

        homeRobot.clickOnProfilesTab().isSuccess();

        profilesResults.profileIsVisible(UpgradeTestData.profileName);
    }
}

