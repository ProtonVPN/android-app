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

import com.protonvpn.actions.HomeRobot;
import com.protonvpn.android.R;
import com.protonvpn.testsHelper.TestUser;
import com.protonvpn.testsHelper.UIActionsTestHelper;

public class LogoutResult extends UIActionsTestHelper {

    TestUser user;
    public ConnectionResult connectionResult;

    public LogoutResult() {
        connectionResult = new ConnectionResult();
    }

    public LogoutResult(TestUser testUser) {
        user = testUser;
    }

    public LogoutResult isSuccessful() {
        //checks if login form appeared
        checkIfObjectWithIdIsDisplayed(R.id.editEmail);
        checkIfObjectWithIdIsDisplayed(R.id.editPassword);
        checkIfObjectWithIdIsDisplayed(R.id.buttonLogin);

        return new LogoutResult();
    }

    public LogoutResult isFailure() {
        //checks if login form has not appeared
        checkIfObjectWithIdIsNotDisplayed(R.id.editEmail);
        checkIfObjectWithIdIsNotDisplayed(R.id.editPassword);
        checkIfObjectWithIdIsNotDisplayed(R.id.buttonLogin);

        return new LogoutResult();
    }

    public HomeRobot warningMessageIsDisplayed() {

        //check if warning message is displayed
        checkIfObjectWithIdAndTextIsDisplayed(R.id.md_title, R.string.warning);
        checkIfObjectWithIdAndTextIsDisplayed(R.id.md_content,
            "Logging out will disconnect your device from the VPN server.");

        return new HomeRobot();
    }

    public LogoutResult userNameVisible() {
        checkIfObjectWithIdAndTextIsDisplayed(R.id.editEmail, user.email);
        return this;
    }
}

