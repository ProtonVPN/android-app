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
package com.protonvpn.results;

import com.protonvpn.actions.ConnectionRobot;
import com.protonvpn.actions.HomeRobot;
import com.protonvpn.actions.ServiceRobot;
import com.protonvpn.android.R;
import com.protonvpn.testsHelper.ServiceTestHelper;
import com.protonvpn.testsHelper.UIActionsTestHelper;

import androidx.test.rule.ServiceTestRule;

public class ConnectionResult extends UIActionsTestHelper {

    public ConnectionRobot connectionRobot;
    public HomeRobot homeRobot;
    public ServiceRobot serviceRobot;
    public ProfilesResult profilesResult;
    public ServiceTestHelper serviceTestHelper;

    public ConnectionResult() {
        connectionRobot = new ConnectionRobot();
        homeRobot = new HomeRobot();
        serviceRobot = new ServiceRobot();
        profilesResult = new ProfilesResult();
        serviceTestHelper = new ServiceTestHelper(new ServiceTestRule());
    }

    public ConnectionResult isConnectedToVpn() {
        serviceTestHelper.checkIfConnectedToVPN();
        return this;
    }

    public ConnectionResult isDisconnectedFromVpn() {
        serviceTestHelper.checkIfDisconnectedFromVPN();
        return this;
    }

    public ConnectionResult cannotAccessSecureCoreAsFreeUser() {
        checkIfObjectWithIdAndTextIsDisplayed(R.id.md_title, R.string.restrictedSecureCoreTitle);
        checkIfObjectWithIdAndTextIsDisplayed(R.id.md_content, R.string.restrictedSecureCore);
        checkIfObjectWithIdAndTextIsDisplayed(R.id.md_buttonDefaultPositive, "Upgrade");
        checkIfObjectWithIdAndTextIsDisplayed(R.id.md_buttonDefaultNegative, R.string.cancel);
        clickOnObjectWithText(R.string.cancel);
        return this;
    }
}
