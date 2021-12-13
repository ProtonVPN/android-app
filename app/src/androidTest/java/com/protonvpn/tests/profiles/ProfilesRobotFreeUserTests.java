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
package com.protonvpn.tests.profiles;

import com.protonvpn.actions.HomeRobot;
import com.protonvpn.actions.ProfilesRobot;
import com.protonvpn.results.ProfilesResult;
import com.protonvpn.test.shared.TestUser;
import com.protonvpn.tests.testRules.ProtonHomeActivityTestRule;
import com.protonvpn.tests.testRules.SetUserPreferencesRule;

import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;

import java.time.LocalTime;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import dagger.hilt.android.testing.HiltAndroidRule;
import dagger.hilt.android.testing.HiltAndroidTest;

@LargeTest
@RunWith(AndroidJUnit4.class)
@HiltAndroidTest
public class ProfilesRobotFreeUserTests {

    private final ProtonHomeActivityTestRule testRule = new ProtonHomeActivityTestRule();
    @Rule public RuleChain rules = RuleChain
        .outerRule(new HiltAndroidRule(this))
        .around(new SetUserPreferencesRule(TestUser.getFreeUser()))
        .around(testRule);

    //TODO: uncomment when this issue is fixed https://gitlab.com/ProtonVPN/android-app/issues/345
    @Ignore
    @Test
    public void tryToSetToProfileWithFreeUser() {
        HomeRobot homeRobot = new HomeRobot();
        ProfilesRobot profilesRobot = homeRobot.clickOnProfilesTab().isSuccess();

        String profileName = "Test " + LocalTime.now().toString();

        profilesRobot.clickOnCreateNewProfileButton();
        profilesRobot.insertTextInProfileNameField(profileName);
        profilesRobot.selectFirstNotAccessibleVpnCountry();

        ProfilesResult result = profilesRobot.selectRandomServer();
        result.isFailure().nonAccessibleServersVisible();
    }
}
