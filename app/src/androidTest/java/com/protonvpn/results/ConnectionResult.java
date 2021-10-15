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

import com.protonvpn.MockSwitch;
import com.protonvpn.actions.ConnectionRobot;
import com.protonvpn.actions.ServiceRobot;
import com.protonvpn.android.R;
import com.protonvpn.testsHelper.ServiceTestHelper;
import com.protonvpn.testsHelper.UIActionsTestHelper;

import androidx.test.rule.ServiceTestRule;

public class ConnectionResult extends UIActionsTestHelper {

    public ConnectionRobot connectionRobot;
    public ProfilesResult profilesResult;
    public ServiceTestHelper serviceTestHelper;

    public ConnectionResult() {
        connectionRobot = new ConnectionRobot();
        profilesResult = new ProfilesResult();
        if (MockSwitch.mockedConnectionUsed) {
            serviceTestHelper = new ServiceTestHelper();
        }
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
        checkIfObjectWithIdAndTextIsDisplayed(R.id.textTitle, R.string.upgrade_secure_core_title);
        checkIfObjectWithIdAndTextIsDisplayed(R.id.textMessage, R.string.upgrade_secure_core_message);
        checkIfObjectWithIdAndTextIsDisplayed(R.id.buttonShowPlans, R.string.upgrade_see_plans_button);
        checkIfObjectWithIdAndTextIsDisplayed(R.id.buttonClose, R.string.upgrade_not_now_button);
        clickOnObjectWithText(R.string.upgrade_not_now_button);
        return this;
    }
}
