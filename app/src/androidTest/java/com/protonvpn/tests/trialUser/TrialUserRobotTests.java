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
package com.protonvpn.tests.trialUser;

import com.protonvpn.actions.HomeRobot;
import com.protonvpn.results.HomeResult;
import com.protonvpn.tests.testRules.ProtonHomeActivityTestRule;
import com.protonvpn.tests.testRules.SetUserPreferencesRule;
import com.protonvpn.testsHelper.ServiceTestHelper;
import com.protonvpn.testsHelper.TestUser;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.filters.SdkSuppress;

@LargeTest
@RunWith(AndroidJUnit4.class)
public class TrialUserRobotTests {

    HomeResult result = new HomeResult();
    HomeRobot homeRobot = new HomeRobot();

    @Rule public ProtonHomeActivityTestRule testRule = new ProtonHomeActivityTestRule();
    @ClassRule static public SetUserPreferencesRule testClassRule =
        new SetUserPreferencesRule(TestUser.getTrialUser());

    @Test
    @SdkSuppress(minSdkVersion = 26) //When https://jira.protontech.ch/browse/VPNAND-268 is fixed remove it.
    public void checkIfDialogWelcomeIsVisibleAndButtonWorks() {
        result.dialogWelcomeIsVisible();
        homeRobot.clickButtonGotIt().isSuccessful();
    }

    @Test
    public void checkExpiredTrialUserNotification() {
        ServiceTestHelper.getExpiredTrialUserNotification(testRule.getActivity());
        result.dialogAttentionTrialExpiredIsVisible();
        homeRobot.clickButtonCancel().isSuccessful();
    }

    @Test
    public void checkIfButtonUpgradeWorksAndHasLInk() {
        ServiceTestHelper.getExpiredTrialUserNotification(testRule.getActivity());
        homeRobot.clickButtonUpgrade().upgradeButtonHasLink();
    }
}

