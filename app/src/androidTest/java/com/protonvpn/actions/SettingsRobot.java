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
package com.protonvpn.actions;

import com.protonvpn.android.R;
import com.protonvpn.tests.upgrade.UpgradeTestData;
import com.protonvpn.testsHelper.ConditionalActionsHelper;
import com.protonvpn.testsHelper.UIActionsTestHelper;
import com.protonvpn.results.SettingsResults;

public class SettingsRobot extends UIActionsTestHelper {

    public SettingsRobot navigateBackToHomeScreen() {
        clickOnObjectWithContentDescription("Navigate up");
        waitUntilObjectWithTextAppearsInView("PROFILES");
        return this;
    }

    public SettingsRobot setFastestQuickConnection() {
        clickOnObjectWithId(R.id.spinnerDefaultConnection);
        clickOnObjectWithText("Fastest");
        checkIfObjectWithTextIsDisplayed("Fastest");
        return this;
    }

    public SettingsRobot setRandomQuickConnection() {
        clickOnObjectWithId(R.id.spinnerDefaultConnection);
        clickOnObjectWithText("Random");
        checkIfObjectWithTextIsDisplayed("Random");
        return this;
    }

    public SettingsResults setMTU(int mtu) {
        ConditionalActionsHelper.scrollDownInViewWithIdUntilObjectWithTextAppears(R.id.scrollView,
            R.string.settingsMtuDescription);
        insertTextIntoFieldWithId(R.id.textMTU, String.valueOf(mtu));
        return new SettingsResults();
    }

    public SettingsResults toggleOnSplitTunneling() {
        ConditionalActionsHelper.scrollDownInViewWithIdUntilObjectWithTextAppears(R.id.scrollView,
            "Split tunneling allows certain apps or IPs to be excluded from the VPN traffic.");
        clickOnObjectWithContentDescription(R.string.splitTunnellingSwitch);
        return new SettingsResults();
    }

    public SettingsRobot toggleOffSplitTunneling() {
        clickOnObjectWithContentDescription(R.string.splitTunnellingSwitch);
        return this;
    }

    public SettingsRobot openExcludedIPAddressesList() {
        clickOnObjectWithContentDescription("Exclude IP addresses");
        return this;
    }

    public SettingsRobot clickOnDoneButton() {
        clickOnObjectWithText("DONE");
        return this;
    }

    public SettingsRobot addIpAddressInSplitTunneling() {
        openExcludedIPAddressesList();
        insertTextIntoFieldWithContentDescription("Add IP Address", UpgradeTestData.excludedIPAddress);
        clickOnObjectWithText("ADD");
        clickOnDoneButton();
        return this;
    }

}
