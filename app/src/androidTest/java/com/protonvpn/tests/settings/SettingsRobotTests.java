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
package com.protonvpn.tests.settings;

import com.protonvpn.actions.SettingsRobot;
import com.protonvpn.results.SettingsResults;
import com.protonvpn.tests.testRules.ProtonSettingsActivityTestRule;
import com.protonvpn.tests.testRules.SetUserPreferencesRule;
import com.protonvpn.test.shared.TestUser;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;

@LargeTest
@RunWith(AndroidJUnit4.class)
public class SettingsRobotTests {

    SettingsRobot settingsRobot = new SettingsRobot();

    @Rule public ProtonSettingsActivityTestRule testRule = new ProtonSettingsActivityTestRule();

    @ClassRule static public SetUserPreferencesRule testClassRule =
        new SetUserPreferencesRule(TestUser.getPlusUser());

    @Test
    public void checkIfSettingsViewIsVisible() {
        SettingsResults results = new SettingsResults();
        results.settingsViewIsVisible();
    }

    @Test
    public void selectFastestQuickConnection() {
        settingsRobot.setFastestQuickConnection();
    }

    @Test
    public void setTooLowMTU() {
        SettingsResults result = settingsRobot.openMtuSettings().setMTU(1279);
        result.checkIfMtuSaveIsDisabled();
    }

    @Test
    public void setTooHighMTU() {
        SettingsResults result = settingsRobot.openMtuSettings().setMTU(1501);
        result.checkIfMtuSaveIsDisabled();
    }

    @Test
    public void switchSplitTunneling() {
        SettingsResults toggle = settingsRobot.toggleSplitTunneling();
        toggle.splitTunnelIPIsVisible();
        settingsRobot.toggleSplitTunneling();
    }
}
