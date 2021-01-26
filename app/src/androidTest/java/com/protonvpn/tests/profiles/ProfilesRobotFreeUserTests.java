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
import com.protonvpn.tests.testRules.ProtonHomeActivityTestRule;
import com.protonvpn.tests.testRules.SetUserPreferencesRule;
import com.protonvpn.test.shared.TestUser;

import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.LocalTime;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.rule.ServiceTestRule;

@LargeTest
@RunWith(AndroidJUnit4.class)
public class ProfilesRobotFreeUserTests {

    private HomeRobot homeRobot = new HomeRobot();

    @Rule public ProtonHomeActivityTestRule testRule = new ProtonHomeActivityTestRule();

    @ClassRule public final static ServiceTestRule SERVICE_TEST_RULE = new ServiceTestRule();
    @ClassRule static public SetUserPreferencesRule testClassRule =
        new SetUserPreferencesRule(TestUser.getFreeUser());

    //TODO: uncomment when this issue is fixed https://gitlab.com/ProtonVPN/android-app/issues/345
    @Ignore
    @Test
    public void tryToSetToProfileWithFreeUser() {
        ProfilesRobot profilesRobot = homeRobot.clickOnProfilesTab().isSuccess();

        String profileName = "Test " + LocalTime.now().toString();

        profilesRobot.clickOnCreateNewProfileButton();
        profilesRobot.insertTextInProfileNameField(profileName);
        profilesRobot.selectFirstNotAccessibleVpnCountry();

        ProfilesResult result = profilesRobot.selectNonAccessibleRandomServer();
        result.isFailure().nonAccessibleServersVisible();
    }
}
