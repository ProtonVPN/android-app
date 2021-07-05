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
import com.protonvpn.actions.ProfilesRobot;
import com.protonvpn.actions.SettingsRobot;
import com.protonvpn.results.ProfilesResult;
import com.protonvpn.tests.testRules.ProtonLoginActivityTestRule;
import com.protonvpn.tests.testRules.SetUserPreferencesForUpgradeRule;
import com.protonvpn.test.shared.TestUser;

import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
    /*
    Instructions how to use upgrade tests:

    1. Basic Idea:
        1. Install and use your "old" app
        2. Save data via adb pull /data/data/com.protonvpn $LOCALDIR
        3. Update to your new app version via IDE or adb
        4. Test your migration
        No need to install the old app again from now on.
        For retrying the migration: 1. adb push $LOCALDIR /data/data/com.company.app 2. Test your migration
         3. Fix your migration code and repeat



    2. Commands to use:
        To start using adb:
            //Commands in command line
            adb kill-server
            adb devices


        //to turn of the animations: 
        adb shell settings put global window_animation_scale 0
        adb shell settings put global transition_animation_scale 0
        adb shell settings put global animator_duration_scale 0


        To install apk and test apk:
        adb push /Users/tomasmarciulionis/Desktop/Proton_Android/android-app/app/build/outputs/apk/prod
        /debug/ProtonVPN-1.3.8(73)-prod-debug.apk /data/local/tmp/com.protonvpn.android
        adb shell pm install -t -r "/data/local/tmp/com.protonvpn.android"

        adb push /Users/tomasmarciulionis/Desktop/Proton_Android/android-app/app/build/outputs/apk
        /androidTest/prod/debug/ProtonVPN-1.3.8(73)-prod-debug-androidTest.apk /data/local/tmp/com
        .protonvpn.android.test
        adb shell pm install -t -r "/data/local/tmp/com.protonvpn.android.test"

        Running test from the terminal:

        $ adb shell am instrument -w -r   -e debug false -e class 'com.protonvpn.tests.upgrade
        .UpgradeTests' com.protonvpn.android.test/com.protonvpn.TestsRunner

        To check app:
        $ adb shell am instrument -w -r   -e debug false -e class 'com.protonvpn.tests.upgrade
        .CheckUpgradeTests' com.protonvpn.android.test/com.protonvpn.TestsRunner
     */

@Ignore
@LargeTest
@RunWith(AndroidJUnit4.class)
public class UpgradeTests {

    private HomeRobot homeRobot = new HomeRobot();

    @Rule public ProtonLoginActivityTestRule testRule = new ProtonLoginActivityTestRule();
    @ClassRule public static SetUserPreferencesForUpgradeRule testClassRule =
        new SetUserPreferencesForUpgradeRule(null);

    @Test
    public void setSettings() {
        LoginRobot loginRobot = new LoginRobot();
        loginRobot.login(TestUser.getPlusUser());

        SettingsRobot settingsRobot = homeRobot.openSettings();

        settingsRobot.setRandomQuickConnection();

        settingsRobot.setMTU(UpgradeTestData.valueMTU);

        settingsRobot.toggleOnSplitTunneling();

        settingsRobot.addIpAddressInSplitTunneling();

        settingsRobot.toggleOffSplitTunneling();

        settingsRobot.navigateBackToHomeScreen();
    }

    @Test
    public void setProfile() {
        LoginRobot loginRobot = new LoginRobot();
        loginRobot.login(TestUser.getPlusUser());

        ProfilesRobot profilesRobot = homeRobot.clickOnProfilesTab().isSuccess();

        profilesRobot.clickOnCreateNewProfileButton();
        profilesRobot.insertTextInProfileNameField(UpgradeTestData.profileName);
        profilesRobot.selectFirstCountry();
        profilesRobot.selectRandomServer();

        ProfilesResult result = profilesRobot.clickOnSaveButton();
        result.isSuccess().getProfilesResult().profileIsVisible(UpgradeTestData.profileName);
    }
}

