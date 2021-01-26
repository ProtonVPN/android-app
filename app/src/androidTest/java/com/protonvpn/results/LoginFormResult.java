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

import com.protonvpn.android.R;
import com.protonvpn.test.shared.TestUser;
import com.protonvpn.testsHelper.UIActionsTestHelper;

public class LoginFormResult extends UIActionsTestHelper {

    private TestUser user;

    public LoginFormResult(TestUser testUser) {
        user = testUser;
    }

    public LoginFormResult() {}

    public LoginFormResult helpMenuIsVisible() {
        //checks if the button exists
        checkIfObjectWithIdIsDisplayed(R.id.buttonResetPassword);
        checkIfObjectWithIdIsDisplayed(R.id.buttonForgotUser);
        checkIfObjectWithIdIsDisplayed(R.id.buttonLoginProblems);
        checkIfObjectWithIdIsDisplayed(R.id.buttonGetSupport);

        //clicks the Cancel button
        clickOnObjectWithIdAndText(R.id.md_buttonDefaultNegative, R.string.cancel);

        //checks if the login screen reappears
        checkIfObjectWithIdIsDisplayed(R.id.editEmail);
        checkIfObjectWithIdIsDisplayed(R.id.editPassword);
        checkIfObjectWithIdIsDisplayed(R.id.buttonLogin);

        return this;
    }

    public LoginFormResult passwordIsVisible() {
        checkIfObjectWithIdAndTextIsDisplayed(R.id.editPassword, user.password);
        return this;
    }
}
