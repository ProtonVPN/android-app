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
import com.protonvpn.test.shared.TestUser;
import com.protonvpn.tests.testRules.ProtonHomeActivityTestRule;
import com.protonvpn.tests.testRules.SetUserPreferencesRule;
import com.protonvpn.testsHelper.ServiceTestHelper;

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
public class TrialUserRobotTests {

    private final ProtonHomeActivityTestRule testRule = new ProtonHomeActivityTestRule();
    @Rule public RuleChain rules = RuleChain
        .outerRule(new HiltAndroidRule(this))
        .around(new SetUserPreferencesRule(TestUser.getTrialUser()))
        .around(testRule);

    private HomeRobot homeRobot;
    private ServiceTestHelper serviceTestHelper;

    @Before
    public void setup() {
        homeRobot = new HomeRobot();
        serviceTestHelper = new ServiceTestHelper();
    }

    @Test
    @SdkSuppress(minSdkVersion = 26) //When https://jira.protontech.ch/browse/VPNAND-268 is fixed remove it.
    public void checkIfDialogWelcomeIsVisibleAndButtonWorks() {
        new HomeResult().dialogWelcomeIsVisible();
        homeRobot.clickButtonGotIt().isSuccessful();
    }

    @Test
    public void checkExpiredTrialUserNotification() {
        serviceTestHelper.getExpiredTrialUserNotification(testRule.getActivity());
        new HomeResult().dialogAttentionTrialExpiredIsVisible();
        homeRobot.clickButtonCancel().isSuccessful();
    }

    @Test
    public void checkIfButtonUpgradeWorksAndHasLInk() {
        serviceTestHelper.getExpiredTrialUserNotification(testRule.getActivity());
        homeRobot.clickButtonUpgrade().upgradeButtonHasLink();
    }
}

